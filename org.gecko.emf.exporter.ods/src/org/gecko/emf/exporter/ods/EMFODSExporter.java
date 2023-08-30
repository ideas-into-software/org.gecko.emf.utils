/**
 * Copyright (c) 2012 - 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.exporter.ods;

import static java.util.stream.Collectors.toList;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.text.WordUtils;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExportOptions;
import org.gecko.emf.exporter.EMFExporter;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.miachm.sods.Color;
import com.github.miachm.sods.LinkedValue;
import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to ODS format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFODSExporter", scope = ServiceScope.PROTOTYPE)
public class EMFODSExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFODSExporter.class);

	private static final int MAX_CHAR_PER_LINE_DEFAULT = 30;

	private static final Style HEADER_STYLE = new Style();
	static {
		HEADER_STYLE.setBackgroundColor(new Color("#a3a3a3"));
		HEADER_STYLE.setFontColor(new Color("#000000"));
		HEADER_STYLE.setBold(true);
		HEADER_STYLE.setTextAligment(Style.TEXT_ALIGMENT.Center);
	}

	private static final Style WRAPPED_DATA_CELL_STYLE = new Style();
	static {
		WRAPPED_DATA_CELL_STYLE.setWrap(true);
	}

	private static final List<String> METADATA_ECLASS_SHEET_HEADERS = List.of("Name", "Type", "isMany", "isRequired",
			"Default value", "Documentation");
	private static final List<String> METADATA_EENUM_SHEET_HEADERS = List.of("Name", "Literal", "Value",
			"Documentation");
	private static final String METADATA_DOCUMENTATION_HEADER = "Documentation";
	
	private static final String METADATA_SHEET_SUFFIX = "Metadata";
	private static final String MAPPING_TABLE_SHEET_SUFFIX = "Mapping Table";

	private static final String DOCUMENTATION_GENMODEL_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
	private static final String DOCUMENTATION_GENMODEL_DETAILS = "documentation";

	private static final String ECORE_PACKAGE_NAME = "ecore";

	private static final String ID_COLUMN_NAME = "Id";
	private static final int ID_COLUMN_WIDTH = 18;

	private static final String REF_COLUMN_PREFIX = "ref_";

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExporter#exportResourceTo(org.eclipse.emf.ecore.resource.Resource, java.io.OutputStream, java.util.Map)
	 */
	@Override
	public void exportResourceTo(Resource resource, OutputStream outputStream, Map<?, ?> options)
			throws EMFExportException {
		Objects.requireNonNull(resource, "Resource is required for export!");

		try {

			exportEObjectsTo(resource.getContents(), outputStream, options);

		} catch (Exception e) {
			throw new EMFExportException(e);
		}
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExporter#exportEObjectsTo(java.util.List, java.io.OutputStream, java.util.Map)
	 */
	@Override
	public void exportEObjectsTo(List<EObject> eObjects, OutputStream outputStream, Map<?, ?> options)
			throws EMFExportException {
		Objects.requireNonNull(eObjects, "At least one EObject is required for export!");
		Objects.requireNonNull(outputStream, "Output stream is required for export!");

		if (!eObjects.isEmpty()) {

			try {

				final Map<Object, Object> exportOptions = validateExportOptions(options);

				LOG.info("Starting export of {} EObject(s) to ODS format" + (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				if (!exportOptions.isEmpty()) {
					LOG.info("  Locale to use: {}", locale(exportOptions));
					LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
					LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
					LOG.info("  Adjust column width: {}", adjustColumnWidthEnabled(exportOptions));
					LOG.info("  Generate links for references: {}", generateLinksEnabled(exportOptions));
					LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
				}

				SpreadSheet document = new SpreadSheet();

				// maps sheet names to instances of sheets
				final Map<String, Sheet> eClassesSheets = new HashMap<String, Sheet>();

				// maps EObjects' unique identifiers to instances of sheets, so those can be
				// looked up e.g. when constructing links
				final Map<String, Sheet> eObjectsSheets = new HashMap<String, Sheet>();

				// stores EObjects' EClasses - used e.g. to construct meta data
				final Set<EClass> eObjectsClasses = new HashSet<EClass>();

				// stores EEnums - used e.g. to construct meta data
				final Set<EEnum> eObjectsEnums = new HashSet<EEnum>();

				// maps EObjects' unique identifiers to pseudo IDs - for those EObjects which
				// lack id field
				final Map<String, String> eObjectsPseudoIDs = new HashMap<String, String>();

				final List<EObject> eObjectsSafeCopy = safeCopy(eObjects);

				// pseudo IDs are needed before main processing starts
				generatePseudoIDs(eObjectsSafeCopy, eObjectsPseudoIDs);

				// @formatter:off
				createSheets(document, 
						eClassesSheets, 
						eObjectsClasses, 
						eObjectsEnums,
						eObjectsPseudoIDs, 
						eObjectsSheets,
						eObjectsSafeCopy, 
						exportOptions);
				// @formatter:on

				// @formatter:off
				createSheetsData(document, 
						eClassesSheets, 
						eObjectsClasses, 
						eObjectsEnums,
						eObjectsPseudoIDs, 
						eObjectsSheets,
						eObjectsSafeCopy, 
						exportOptions);
				// @formatter:on

				if (exportMetadataEnabled(exportOptions)) {
					exportMetadata(document, eObjectsClasses, eObjectsEnums, exportOptions);
				}

				document.save(outputStream);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void generatePseudoIDs(List<EObject> eObjects, Map<String, String> eObjectsPseudoIDs) {
		LOG.debug("Generating pseudo IDs");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		generatePseudoIDs(eObjects, processedEObjectsIdentifiers, eObjectsPseudoIDs);
	}

	private void generatePseudoIDs(List<EObject> eObjects, Set<String> processedEObjectsIdentifiers,
			Map<String, String> eObjectsPseudoIDs) {
		for (EObject eObject : eObjects) {
			if (isProcessed(processedEObjectsIdentifiers, eObject)) {
				continue;
			}

			processedEObjectsIdentifiers.add(getEObjectIdentifier(eObject));

			generatePseudoID(eObject, eObjectsPseudoIDs);

			eObject.eClass().getEAllReferences().stream().forEach(eReference -> {
				generatePseudoID(eObject, eReference, processedEObjectsIdentifiers, eObjectsPseudoIDs);
			});
		}
	}

	@SuppressWarnings("unchecked")
	private void generatePseudoID(EObject eObject, EReference eReference, Set<String> processedEObjectsIdentifiers,
			Map<String, String> eObjectsPseudoIDs) {
		Object value = eObject.eGet(eReference);
		if (value != null) {
			if (!eReference.isMany() && value instanceof EObject) {
				generatePseudoID((EObject) value, eObjectsPseudoIDs);
			} else if (eReference.isMany()) {
				generatePseudoIDs((List<EObject>) value, processedEObjectsIdentifiers, eObjectsPseudoIDs);
			}
		}
	}

	private void generatePseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		if (!hasID(eObject, eObjectsPseudoIDs)) {
			LOG.debug("Generating pseudo ID for EObject ID '{}' named '{}'", getEObjectIdentifier(eObject), eObject.eClass().getName());
			eObjectsPseudoIDs.put(getEObjectIdentifier(eObject), UUID.randomUUID().toString());
		}
	}

	private boolean hasID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return (hasID(eObject) || hasPseudoID(eObject, eObjectsPseudoIDs));
	}

	private boolean hasID(EObject eObject) {
		return (getID(eObject) != null);
	}

	private String getID(EObject eObject) {
		return EcoreUtil.getID(eObject);
	}

	private boolean hasPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return (eObjectsPseudoIDs.containsKey(getEObjectIdentifier(eObject)));
	}

	private String getPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return eObjectsPseudoIDs.get(getEObjectIdentifier(eObject));
	}

	private void createSheets(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			List<EObject> eObjects, Map<Object, Object> exportOptions) {
	
		LOG.debug("Creating sheets");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createSheetForEObjectWithEReferences(document, 
					eClassesSheets, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsSheets,
					exportOptions,
					eObject);
			// @formatter:on
		}
	}

	private void createSheetsData(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			List<EObject> eObjects, Map<Object, Object> exportOptions) throws EMFExportException {

		LOG.debug("Creating sheets' data");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createSheetDataForEObjectWithEReferences(document, 
					eClassesSheets, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsSheets,
					exportOptions,
					eObject);
			// @formatter:on
		}
	}

	private void createSheetForEObjectWithEReferences(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions, EObject eObject) {
		createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums, eObjectsPseudoIDs,
				eObjectsSheets, exportOptions, eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			createSheetForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
					eObjectsPseudoIDs, eObjectsSheets, exportOptions, eObject, r);
		});
	}

	private void createSheetDataForEObjectWithEReferences(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions, EObject eObject) throws EMFExportException {
		createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets, exportOptions,
				eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			try {
				createSheetDataForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs,
						eObjectsSheets, exportOptions, eObject, r);
			} catch (EMFExportException e) {
				e.printStackTrace();
			}
		});
	}

	private void createSheet(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<String> eObjectsIdentifiers,
			Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums, Map<String, String> eObjectsPseudoIDs,
			Map<String, Sheet> eObjectsSheets, Map<Object, Object> exportOptions, EObject... eObjects) {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			LOG.debug("Creating sheet named '{}'", constructEClassSheetName(eClass));

			Sheet sheet = getOrAddSheet(document, eClassesSheets, eClass, eObjectsEnums,
					hasPseudoID(eObjects[0], eObjectsPseudoIDs), exportOptions);

			for (EObject eObject : eObjects) {
				String eObjectIdentifier = getEObjectIdentifier(eObject);

				eObjectsIdentifiers.add(eObjectIdentifier);
				eObjectsClasses.add(eObject.eClass());
				eObjectsSheets.put(eObjectIdentifier, sheet);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					createSheetForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses,
							eObjectsEnums, eObjectsPseudoIDs, eObjectsSheets, exportOptions, eObject, r);
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createSheetForEReference(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions, EObject eObject, EReference r) {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
						eObjectsPseudoIDs, eObjectsSheets, exportOptions, (EObject) value);
			} else if (r.isMany()) {
				createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
						eObjectsPseudoIDs, eObjectsSheets, exportOptions,
						((List<EObject>) value).toArray(EObject[]::new));
			}
		}
	}

	private void createSheetData(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<String> eObjectsIdentifiers, Map<String, String> eObjectsPseudoIDs,
			Map<String, Sheet> eObjectsSheets, Map<Object, Object> exportOptions, EObject... eObjects)
			throws EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Sheet sheet = getSheet(eClassesSheets, eClass);

			for (EObject eObject : eObjects) {
				eObjectsIdentifiers.add(getEObjectIdentifier(eObject));

				createSheetData(document, sheet, eObject, eObjectsPseudoIDs, eObjectsSheets, exportOptions);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					try {
						createSheetDataForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs,
								eObjectsSheets, exportOptions, eObject, r);
					} catch (EMFExportException e) {
						e.printStackTrace();
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createSheetDataForEReference(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<String> eObjectsIdentifiers, Map<String, String> eObjectsPseudoIDs,
			Map<String, Sheet> eObjectsSheets, Map<Object, Object> exportOptions, EObject eObject, EReference r)
			throws EMFExportException {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets,
						exportOptions, (EObject) value);
			} else if (r.isMany()) {
				createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets,
						exportOptions, ((List<EObject>) value).toArray(EObject[]::new));
			}
		}
	}

	private void createSheetData(SpreadSheet document, Sheet sheet, EObject eObject,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {

		LOG.debug("Creating data for sheet named '{}'", sheet.getName());

		sheet.appendRow();

		Range sheetDataRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

		List<EStructuralFeature> eAllStructuralFeatures = eObject.eClass().getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {

			createSheetDataCell(document, sheetDataRow, colIndex, eObject, eAllStructuralFeatures.get(colIndex),
					eObjectsPseudoIDs, eObjectsSheets, exportOptions);
		}

		if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			LOG.debug("Setting pseudo ID for sheet named '{}'", sheet.getName());

			setStringValueCell(sheetDataRow, columnsCount, eObjectsPseudoIDs.get(getEObjectIdentifier(eObject)));
		}
	}

	private Sheet getOrAddSheet(SpreadSheet document, Map<String, Sheet> eClassesSheets, EClass eClass,
			Set<EEnum> eObjectsEnums, boolean hasPseudoID, Map<Object, Object> exportOptions) {
		String sheetName = constructEClassSheetName(eClass);

		boolean sheetExists = eClassesSheets.containsKey(sheetName);

		Sheet sheet;
		if (sheetExists) {
			sheet = eClassesSheets.get(sheetName);

			LOG.debug("Sheet ID '{}' named '{}' already exists!", sheet.hashCode(), sheetName);

		} else {
			sheet = new Sheet(sheetName);
			document.appendSheet(sheet);
			eClassesSheets.put(sheetName, sheet);

			LOG.debug("Sheet ID '{}' named '{}' did not exist yet!", sheet.hashCode(), sheetName);

			createSheetHeader(sheet, eClass, eObjectsEnums, hasPseudoID, exportOptions);

			if (freezeHeaderRowEnabled(exportOptions)) {
				// TODO: freezing rows is currently not supported in SODS
				// freezeTableHeader(document, sheet, 1, headersCount);
			}

			if (exportMetadataEnabled(exportOptions)) {
				LOG.debug("Adding metadata sheet for {}", sheetName);

				addMetadataSheet(document, eClass);
			}
		}

		return sheet;
	}

	private Sheet getSheet(Map<String, Sheet> eObjectsSheets, EClass eClass) throws EMFExportException {
		String sheetName = constructEClassSheetName(eClass);

		if (!eObjectsSheets.containsKey(sheetName)) {
			throw new EMFExportException("Sheet '" + sheetName + "' does not exist!");
		}

		return eObjectsSheets.get(sheetName);
	}

	private void createSheetHeader(Sheet sheet, EClass eClass, Set<EEnum> eObjectsEnums, boolean hasPseudoID,
			Map<Object, Object> exportOptions) {

		LOG.debug("Creating header for sheet named '{}'" + (hasPseudoID ? " with pseudo ID column" : " without pseudo ID column"), sheet.getName());

		List<EStructuralFeature> eAllStructuralFeatures = eClass.getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		LOG.debug("Sheet named '{}' has {} column(s) based on number of structure features", sheet.getName(), columnsCount);

		sheet.appendColumns((hasPseudoID ? (columnsCount + 1) : columnsCount) - 1); // newly created sheet already has
																					// one column

		LOG.debug("Sheet named '{}' has {} total column(s)", sheet.getName(), sheet.getMaxColumns());

		Range sheetHeaderRow = sheet.getRange(0, 0, 1, sheet.getMaxColumns()); // newly created sheet already has one
																				// row
		sheetHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {

			EStructuralFeature eStructuralFeature = eAllStructuralFeatures.get(colIndex);

			if (isEcoreEEnumDataType(eStructuralFeature)) {
				eObjectsEnums.add(extractEEnumDataType(eStructuralFeature));
			}

			createSheetHeaderCell(sheetHeaderRow, eStructuralFeature, exportOptions, colIndex);
		}

		if (hasPseudoID) {
			createSheetHeaderCell(sheetHeaderRow, "id", exportOptions, columnsCount);
		}
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, EStructuralFeature eStructuralFeature,
			Map<Object, Object> exportOptions, int colIndex) {
		String sheetHeaderName = constructSheetHeaderName(eStructuralFeature);

		createSheetHeaderCell(sheetHeaderRow, sheetHeaderName, exportOptions, colIndex);
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, String sheetHeaderName, Map<Object, Object> exportOptions,
			int colIndex) {

		createSheetHeaderCell(sheetHeaderRow, colIndex, sheetHeaderName);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(sheetHeaderRow.getSheet(), colIndex, adjustColumnWidthCharsCount(sheetHeaderName));
		}
	}

	private int adjustColumnWidthCharsCount(String sheetHeaderName) {
		if (sheetHeaderName.equalsIgnoreCase(ID_COLUMN_NAME) || sheetHeaderName.endsWith(ID_COLUMN_NAME)
				|| sheetHeaderName.startsWith(REF_COLUMN_PREFIX)) {
			return ID_COLUMN_WIDTH;
		} else {
			return sheetHeaderName.length();
		}
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, int colIndex, String sheetHeaderName) {
		sheetHeaderRow.getCell(0, colIndex).setValue(sheetHeaderName);
	}

	private void adjustColumnWidth(Sheet sheet, int colIndex, int charsCount) {
		if (sheet.getColumnWidth(colIndex) == null) {
			sheet.setColumnWidth(colIndex, calculateColumnWidth(charsCount));
		}
	}

	private Double calculateColumnWidth(int charsCount) {
		return (Double.valueOf(charsCount) * (charsCount > 3 ? 5 : 7.5));
	}

	private void adjustRowHeight(Sheet sheet, int rowIndex, String value) {
		sheet.setRowHeight(rowIndex, calculateRowHeight(value));
	}

	private Double calculateRowHeight(String value) {
		return (Double.valueOf(value.length() / MAX_CHAR_PER_LINE_DEFAULT) * 5);
	}

	
	@SuppressWarnings("unused")
	private void freezeSheetHeaderRow(Sheet sheet, int rowCount, int colCount) {
		// TODO: freezing rows is currently not supported in SODS
	}

	private String constructSheetHeaderName(EStructuralFeature eStructuralFeature) {
		StringBuilder sb = new StringBuilder(100);
		if (eStructuralFeature instanceof EReference) {
			sb.append(REF_COLUMN_PREFIX);
		}
		sb.append(eStructuralFeature.getName());
		return sb.toString();
	}

	private boolean isEcoreEEnumDataType(EStructuralFeature eStructuralFeature) {
		return (eStructuralFeature instanceof EAttribute
				&& ((EAttribute) eStructuralFeature).getEAttributeType() instanceof EEnum);
	}

	private EEnum extractEEnumDataType(EStructuralFeature eStructuralFeature) {
		return ((EEnum) ((EAttribute) eStructuralFeature).getEAttributeType());
	}

	private void createSheetDataCell(SpreadSheet document, Range sheetDataRow, int colIndex, EObject eObject,
			EStructuralFeature eStructuralFeature, Map<String, String> eObjectsPseudoIDs,
			Map<String, Sheet> eObjectsSheets, Map<Object, Object> exportOptions) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			Object value = eObject.eGet(eAttribute);
			if (value != null) {
				if (!eAttribute.isMany()) {

					if (value instanceof Date) {
						setDateValueCell(sheetDataRow, colIndex, (Date) value);

					} else if (value instanceof Number) {
						setNumberValueCell(sheetDataRow, colIndex, (Number) value);

					} else if (value instanceof Boolean) {
						setBooleanValueCell(sheetDataRow, colIndex, (Boolean) value);

					} else if (value instanceof byte[]) {
						// TODO: clarify how byte arrays should be handled
						setStringValueCell(sheetDataRow, colIndex, "EAttribute: byte[]");

					} else {
						setStringValueCell(sheetDataRow, colIndex,
								EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
					}

				} else {
					setMultiValueCell(sheetDataRow, colIndex, eAttribute, value);
				}

			} else {
				setVoidValueCell(sheetDataRow, colIndex);
			}

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setEReferenceValueCell(document, sheetDataRow, colIndex, eObject, eReference, eObjectsPseudoIDs,
					eObjectsSheets, exportOptions);
		}
	}

	@SuppressWarnings("unchecked")
	private void setEReferenceValueCell(SpreadSheet document, Range sheetDataRow, int colIndex, EObject eObject,
			EReference r, Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {
		Object value = eObject.eGet(r);

		if (value != null) {
			if ((!r.isMany() && value instanceof EObject) || (r.isMany() && ((List<EObject>) value).size() == 1)) {
				setOneEReferenceValueCell(sheetDataRow, colIndex,
						(!r.isMany() ? ((EObject) value) : ((List<EObject>) value).get(0)), eObjectsPseudoIDs,
						eObjectsSheets, exportOptions);

			} else if (r.isMany() && !((List<EObject>) value).isEmpty()) {
				if (addMappingTableEnabled(exportOptions)) {
					createEReferencesMappingTable(document, sheetDataRow, colIndex, eObject, r, ((List<EObject>) value),
							eObjectsPseudoIDs, eObjectsSheets, exportOptions);

				} else {
					setManyEReferencesValueCell(sheetDataRow, colIndex, ((List<EObject>) value), eObjectsPseudoIDs,
							eObjectsSheets, exportOptions);
				}
			}
		}
	}

	private void createEReferencesMappingTable(SpreadSheet document, Range sheetDataRow, int colIndex,
			EObject fromEObject, EReference toEReference, List<EObject> toEObjects,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {

		Sheet mappingTableSheet = getOrAddMappingTableSheet(document, fromEObject.eClass(), toEReference.getName(),
				toEReference.getEReferenceType(), exportOptions);

		setReferenceToMappingTable(mappingTableSheet, sheetDataRow, colIndex, exportOptions);

		createMappingTableSheetData(mappingTableSheet, fromEObject, toEObjects, eObjectsPseudoIDs, eObjectsSheets,
				exportOptions);
	}

	private void setReferenceToMappingTable(Sheet mappingTableSheet, Range sheetDataRow, int colIndex,
			Map<Object, Object> exportOptions) {

		if (generateLinksEnabled(exportOptions)) {
			setLinkedIDEReferenceValueCell(sheetDataRow, colIndex,
					constructMappingTableEReferenceValue(mappingTableSheet.getName()), mappingTableSheet);
		} else {
			setNonLinkedIDEReferenceValueCell(sheetDataRow, colIndex,
					constructMappingTableEReferenceValue(mappingTableSheet.getName()));
		}
	}

	private String constructMappingTableEReferenceValue(String mappingTableSheetName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append("See: ");
		sb.append(mappingTableSheetName);
		return sb.toString();
	}

	private void createMappingTableSheetData(Sheet mappingTableSheet, EObject fromEObject, List<EObject> toEObjects,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {

		for (EObject toEObject : toEObjects) {
			createMappingTableSheetDataRow(mappingTableSheet, fromEObject, toEObject, eObjectsPseudoIDs, eObjectsSheets,
					exportOptions);
		}
	}

	private void createMappingTableSheetDataRow(Sheet sheet, EObject fromEObject, EObject toEObject,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {

		sheet.appendRow();

		Range sheetDataRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

		setOneEReferenceValueCell(sheetDataRow, 0, fromEObject, eObjectsPseudoIDs, eObjectsSheets, exportOptions);

		setOneEReferenceValueCell(sheetDataRow, 1, toEObject, eObjectsPseudoIDs, eObjectsSheets, exportOptions);

	}

	private String constructMappingTableSheetName(EClass fromEClass, String fromFieldName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(fromEClass.getName());
		sb.append("_");
		sb.append(fromFieldName);
		sb.append(" ");
		sb.append("( ");
		sb.append(MAPPING_TABLE_SHEET_SUFFIX);
		sb.append(" )");
		return sb.toString();
	}

	private Sheet getOrAddMappingTableSheet(SpreadSheet document, EClass fromEClass, String fromFieldName,
			EClass toEClass, Map<Object, Object> exportOptions) {
		String mappingTableSheetName = constructMappingTableSheetName(fromEClass, fromFieldName);

		Sheet mappingTableSheet = document.getSheet(mappingTableSheetName);
		if (mappingTableSheet == null) {
			mappingTableSheet = new Sheet(mappingTableSheetName);
			document.appendSheet(mappingTableSheet);

			createMappingTableSheetHeader(mappingTableSheet, fromEClass, toEClass, exportOptions);
		}

		return mappingTableSheet;
	}

	private void createMappingTableSheetHeader(Sheet sheet, EClass fromEClass, EClass toEClass,
			Map<Object, Object> exportOptions) {

		sheet.appendColumn(); // newly created sheet already has one column

		Range sheetHeaderRow = sheet.getRange(0, 0, 1, sheet.getMaxColumns()); // newly created sheet already has one
																				// row

		sheetHeaderRow.setStyle(HEADER_STYLE);

		createMappingTableSheetHeaderCell(sheetHeaderRow, fromEClass, exportOptions, 0);

		createMappingTableSheetHeaderCell(sheetHeaderRow, toEClass, exportOptions, 1);
	}

	private void createMappingTableSheetHeaderCell(Range sheetHeaderRow, EClass eClass,
			Map<Object, Object> exportOptions, int colIndex) {
		String mappingTableSheetHeaderName = constructMappingTableSheetHeaderName(eClass);

		sheetHeaderRow.getCell(0, colIndex).setValue(mappingTableSheetHeaderName);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(sheetHeaderRow.getSheet(), colIndex,
					adjustColumnWidthCharsCount(mappingTableSheetHeaderName));
		}
	}

	private String constructMappingTableSheetHeaderName(EClass eClass) {
		StringBuilder sb = new StringBuilder(100);

		EAttribute idAttribute = eClass.getEIDAttribute();

		if (idAttribute == null || idAttribute.getName().equalsIgnoreCase(ID_COLUMN_NAME)) {
			sb.append(WordUtils.uncapitalize(eClass.getName()));
			sb.append(ID_COLUMN_NAME);
		} else {
			sb.append(idAttribute.getName());
		}

		return sb.toString();
	}

	private void setOneEReferenceValueCell(Range sheetDataRow, int colIndex, EObject eObject,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {
		String eObjectIdentifier = getEObjectIdentifier(eObject);

		if (hasID(eObject)) {
			if (generateLinksEnabled(exportOptions) && eObjectsSheets.containsKey(eObjectIdentifier)) {
				setLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getID(eObject),
						eObjectsSheets.get(eObjectIdentifier));
			} else {
				setNonLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getID(eObject));
			}
		} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			if (generateLinksEnabled(exportOptions) && eObjectsSheets.containsKey(eObjectIdentifier)) {
				setLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getPseudoID(eObject, eObjectsPseudoIDs),
						eObjectsSheets.get(eObjectIdentifier));
			} else {
				setNonLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getPseudoID(eObject, eObjectsPseudoIDs));
			}
		} else {
			setNoIDEReferenceValueCell(sheetDataRow, colIndex, eObject);
		}
	}

	private void setManyEReferencesValueCell(Range sheetDataRow, int colIndex, List<EObject> eObjects,
			Map<String, String> eObjectsPseudoIDs, Map<String, Sheet> eObjectsSheets,
			Map<Object, Object> exportOptions) {

		StringBuilder sb = new StringBuilder();

		List<LinkedValue> linkedValues = new ArrayList<LinkedValue>();

		for (int i = 0; i < eObjects.size(); i++) {
			EObject eObject = eObjects.get(i);

			String eObjectIdentifier = getEObjectIdentifier(eObject);

			if (hasID(eObject)) {
				if (generateLinksEnabled(exportOptions) && eObjectsSheets.containsKey(eObjectIdentifier)) {
					LinkedValue linkedValue = LinkedValue.builder().value(getID(eObject))
							.href(eObjectsSheets.get(eObjectIdentifier)).build();
					linkedValues.add(linkedValue);
				} else {
					sb.append(getID(eObject));
				}
			} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
				if (generateLinksEnabled(exportOptions) && eObjectsSheets.containsKey(eObjectIdentifier)) {
					LinkedValue linkedValue = LinkedValue.builder().value(getPseudoID(eObject, eObjectsPseudoIDs))
							.href(eObjectsSheets.get(eObjectIdentifier)).build();
					linkedValues.add(linkedValue);
				} else {
					sb.append(getPseudoID(eObject, eObjectsPseudoIDs));
				}

			} else {
				sb.append("EReference: " + eObject.eClass().getName());
			}

			if (hasMoreElements(i, eObjects.size())) {
				sb.append(System.lineSeparator());
			}
		}

		if (generateLinksEnabled(exportOptions) && !linkedValues.isEmpty()) {
			setCellLinkedValues(sheetDataRow, colIndex, linkedValues);
		} else {
			setStringValueCell(sheetDataRow, colIndex, sb.toString());
		}
	}

	private void setCellLinkedValues(Range sheetDataRow, int colIndex, List<LinkedValue> linkedValues) {
		sheetDataRow.getCell(0, colIndex).setLinkedValues(linkedValues);
	}

	private boolean hasMoreElements(int currentIndex, int size) {
		return (currentIndex + 1) < size;
	}

	private void setLinkedIDEReferenceValueCell(Range sheetDataRow, int colIndex, String refId, Sheet refSheet) {
		LinkedValue linkedValue = LinkedValue.builder().value(refId).href(refSheet).build();
		sheetDataRow.getCell(0, colIndex).addLinkedValue(linkedValue);
	}

	private void setNonLinkedIDEReferenceValueCell(Range sheetDataRow, int colIndex, String refId) {
		setStringValueCell(sheetDataRow, colIndex, refId);
	}

	private void setNoIDEReferenceValueCell(Range sheetDataRow, int colIndex, EObject eObject) {
		setStringValueCell(sheetDataRow, colIndex, "EReference: " + eObject.eClass().getName());
	}

	private void setStringValueCell(Range sheetDataRow, int colIndex, String value) {
		sheetDataRow.getCell(0, colIndex).setValue(value);

		if (value.length() > MAX_CHAR_PER_LINE_DEFAULT) {
			sheetDataRow.getCell(0, colIndex).setStyle(WRAPPED_DATA_CELL_STYLE);

			adjustRowHeight(sheetDataRow.getSheet(), sheetDataRow.getRow(), value);
		}
	}

	private void setDateValueCell(Range sheetDataRow, int colIndex, Date value) {
		sheetDataRow.getCell(0, colIndex).setValue(value);
	}

	private void setNumberValueCell(Range sheetDataRow, int colIndex, Number value) {
		sheetDataRow.getCell(0, colIndex).setValue(value.floatValue());
	}

	private void setBooleanValueCell(Range sheetDataRow, int colIndex, Boolean value) {
		sheetDataRow.getCell(0, colIndex).setValue(value.booleanValue());
	}

	@SuppressWarnings("unchecked")
	private void setMultiValueCell(Range sheetDataRow, int colIndex, EAttribute eAttribute, Object multiValue) {
		StringBuilder sb = new StringBuilder();

		Collection<Object> values = (Collection<Object>) multiValue;

		if (!values.isEmpty()) {
			Iterator<Object> valuesIt = values.iterator();

			while (valuesIt.hasNext()) {
				sb.append(EcoreUtil.convertToString(eAttribute.getEAttributeType(), valuesIt.next()));

				if (valuesIt.hasNext()) {
					sb.append(System.lineSeparator());
				}
			}
		}

		setStringValueCell(sheetDataRow, colIndex, sb.toString());
	}

	private void setVoidValueCell(Range sheetDataRow, int colIndex) {
		sheetDataRow.getCell(0, colIndex).clear();
	}

	@SuppressWarnings("unused")
	private Locale locale(Map<Object, Object> exportOptions) {
		return ((Locale) exportOptions.getOrDefault(EMFExportOptions.OPTION_LOCALE, Locale.getDefault()));
	}

	private boolean exportNonContainmentEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, Boolean.FALSE));
	}

	private boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.TRUE));
	}

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.TRUE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_GENERATE_LINKS, Boolean.TRUE));
	}

	private boolean addMappingTableEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADD_MAPPING_TABLE, Boolean.TRUE));
	}

	private boolean freezeHeaderRowEnabled(Map<Object, Object> exportOptions) {
		// TODO: freezing rows is currently not supported in SODS
		// return ((boolean)
		// exportOptions.getOrDefault(EMFExportOptions.OPTION_FREEZE_HEADER_ROW,
		// Boolean.FALSE));
		return false;
	}

	private Map<Object, Object> validateExportOptions(Map<?, ?> options) {
		if (options == null) {
			return Collections.emptyMap();
		} else {
			return Map.copyOf(options);
		}
	}

	private void addMetadataSheet(SpreadSheet document, EClass eClass) {
		document.appendSheet(new Sheet(constructEClassMetadataSheetName(eClass)));
	}

	private Sheet addMetadataSheet(SpreadSheet document, EEnum eEnum) {
		Sheet metadataSheet = new Sheet(constructEEnumMetadataSheetName(eEnum));
		document.appendSheet(metadataSheet);
		return metadataSheet;
	}

	private void exportMetadata(SpreadSheet document, Set<EClass> eClasses, Set<EEnum> eEnums,
			Map<Object, Object> exportOptions) {

		LOG.debug("Exporting metadata");

		exportEClassesMetadata(document, eClasses, exportOptions);
		exportEEnumsMetadata(document, eEnums, exportOptions);
	}

	private void exportEClassesMetadata(SpreadSheet document, Set<EClass> eClasses, Map<Object, Object> exportOptions) {
		for (EClass eClass : eClasses) {
			Sheet metadataSheet = document.getSheet(constructEClassMetadataSheetName(eClass));

			maybeSetEClassMetadataDocumentation(metadataSheet, eClass, exportOptions);

			createEClassMetadataSheetHeader(metadataSheet, exportOptions);

			createEClassMetadataSheetData(metadataSheet, eClass);
		}
	}

	private void exportEEnumsMetadata(SpreadSheet document, Set<EEnum> eEnums, Map<Object, Object> exportOptions) {
		for (EEnum eEnum : eEnums) {
			Sheet metadataSheet = addMetadataSheet(document, eEnum);

			maybeSetEEnumMetadataDocumentationValueCell(metadataSheet, eEnum, exportOptions);

			createEEnumMetadataSheetHeader(metadataSheet, exportOptions);

			createEEnumMetadataSheetData(metadataSheet, eEnum);
		}
	}

	private void maybeSetEClassMetadataDocumentation(Sheet metadataSheet, EClass eClass,
			Map<Object, Object> exportOptions) {
		EAnnotation genModelAnnotation = eClass.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(metadataSheet, genModelAnnotation, exportOptions);
		}
	}

	private void setEClassMetadataDocumentationValueCell(Range metadataSheetDataRow,
			Map<Object, Object> exportOptions) {
		metadataSheetDataRow.getCell(0, 0).setValue(METADATA_DOCUMENTATION_HEADER);
		metadataSheetDataRow.getCell(0, 0).setStyle(HEADER_STYLE);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(metadataSheetDataRow.getSheet(), 0,
					adjustColumnWidthCharsCount(METADATA_DOCUMENTATION_HEADER));
		}
	}

	private void maybeSetEEnumMetadataDocumentationValueCell(Sheet metadataSheet, EEnum eEnum,
			Map<Object, Object> exportOptions) {
		EAnnotation genModelAnnotation = eEnum.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(metadataSheet, genModelAnnotation, exportOptions);
		}
	}

	private void setTypeLevelMetadataDocumentation(Sheet metadataSheet, EAnnotation genModelAnnotation,
			Map<Object, Object> exportOptions) {
		Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

		if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {

			metadataSheet.appendColumn();

			Range metadataSheetDocumentationRow = metadataSheet.getRange((metadataSheet.getMaxRows() - 1), 0, 1, 2);

			setEClassMetadataDocumentationValueCell(metadataSheetDocumentationRow, exportOptions);

			setMetadataDocumentationValueCell(metadataSheetDocumentationRow, 1,
					genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS));

			metadataSheet.appendRow();
		}
	}

	private void createEClassMetadataSheetHeader(Sheet metadataSheet, Map<Object, Object> exportOptions) {
		createMetadataSheetHeader(metadataSheet, METADATA_ECLASS_SHEET_HEADERS, exportOptions);
	}

	private void createEEnumMetadataSheetHeader(Sheet metadataSheet, Map<Object, Object> exportOptions) {
		createMetadataSheetHeader(metadataSheet, METADATA_EENUM_SHEET_HEADERS, exportOptions);
	}

	private void createMetadataSheetHeader(Sheet metadataSheet, List<String> headers,
			Map<Object, Object> exportOptions) {
		int columnsCount = headers.size();

		metadataSheet.appendColumns(columnsCount - metadataSheet.getMaxColumns());

		Range metadataSheetHeaderRow = metadataSheet.getRange((metadataSheet.getMaxRows() - 1), 0, 1,
				metadataSheet.getMaxColumns());

		metadataSheetHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < metadataSheet.getMaxColumns(); colIndex++) {
			createMetadataSheetHeaderCell(metadataSheetHeaderRow, colIndex, headers.get(colIndex), exportOptions);
		}
	}

	private void createMetadataSheetHeaderCell(Range metadataSheetHeaderRow, int colIndex, String headerName,
			Map<Object, Object> exportOptions) {
		metadataSheetHeaderRow.getCell(0, colIndex).setValue(headerName);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(metadataSheetHeaderRow.getSheet(), colIndex, adjustColumnWidthCharsCount(headerName));
		}
	}

	private void createEClassMetadataSheetData(Sheet metadataSheet, EClass eClass) {
		eClass.getEAnnotations(); // TODO: clarify regarding instance-level docs

		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {
			createEClassMetadataSheetDataRow(metadataSheet, eStructuralFeature);
		});
	}

	private void createEEnumMetadataSheetData(Sheet metadataSheet, EEnum eEnum) {
		eEnum.getEAnnotations(); // TODO: clarify regarding instance-level docs

		eEnum.getELiterals().forEach(eEnumLiteral -> {
			createEEnumMetadataSheetDataRow(metadataSheet, eEnumLiteral);
		});
	}

	private void createEClassMetadataSheetDataRow(Sheet metadataSheet, EStructuralFeature eStructuralFeature) {
		metadataSheet.appendRow();

		for (int colIndex = 0; colIndex < metadataSheet.getMaxColumns(); colIndex++) {
			Range metadataSheetDataRow = metadataSheet.getRange((metadataSheet.getMaxRows() - 1), 0, 1,
					metadataSheet.getMaxColumns());

			createEClassMetadataSheetDataCell(metadataSheetDataRow, colIndex, eStructuralFeature);
		}
	}

	private void createEEnumMetadataSheetDataRow(Sheet metadataSheet, EEnumLiteral eEnumLiteral) {
		metadataSheet.appendRow();

		for (int colIndex = 0; colIndex < metadataSheet.getMaxColumns(); colIndex++) {
			Range metadataSheetDataRow = metadataSheet.getRange((metadataSheet.getMaxRows() - 1), 0, 1,
					metadataSheet.getMaxColumns());

			createEEnumMetadataSheetDataCell(metadataSheetDataRow, colIndex, eEnumLiteral);
		}
	}

	private void createEClassMetadataSheetDataCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		switch (colIndex) {
		case 0: // Name
			setEClassMetadataNameValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		case 1: // Type
			setEClassMetadataTypeValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		case 2: // isMany
			setEClassMetadataIsManyValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		case 3: // isRequired
			setEClassMetadataIsRequiredValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		case 4: // Default value
			setEClassMetadataDefaultValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		case 5: // Documentation
			setEStructuralFeatureMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
			break;
		}
	}

	private void createEEnumMetadataSheetDataCell(Range metadataSheetDataRow, int colIndex, EEnumLiteral eEnumLiteral) {
		switch (colIndex) {
		case 0: // Name
			setEEnumMetadataNameValueCell(metadataSheetDataRow, colIndex, eEnumLiteral);
			break;
		case 1: // Literal
			setEEnumMetadataLiteralValueCell(metadataSheetDataRow, colIndex, eEnumLiteral);
			break;
		case 2: // Value
			setEEnumMetadataValueValueCell(metadataSheetDataRow, colIndex, eEnumLiteral);
			break;
		case 3: // Documentation
			setEEnumLiteralMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, eEnumLiteral);
			break;
		}
	}

	private void setEClassMetadataNameValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setStringValueCell(metadataSheetDataRow, colIndex, eStructuralFeature.getName());
	}

	private void setEClassMetadataTypeValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			setStringValueCell(metadataSheetDataRow, colIndex,
					normalizeMetadataTypeEAttributeName(eAttribute.getEAttributeType()));

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setStringValueCell(metadataSheetDataRow, colIndex, eReference.getEReferenceType().getName());

		} else {
			setVoidValueCell(metadataSheetDataRow, colIndex);
		}
	}

	private String normalizeMetadataTypeEAttributeName(EDataType eAttributeType) {
		if (isEcoreDataType(eAttributeType)) {
			String instanceClassName = eAttributeType.getInstanceClassName();
			try {
				return Class.forName(instanceClassName).getSimpleName();
			} catch (ClassNotFoundException e) {
				return instanceClassName;
			}
		} else {
			return eAttributeType.getName();
		}
	}

	private boolean isEcoreDataType(EDataType eAttributeType) {
		return eAttributeType.getEPackage().getName().equalsIgnoreCase(ECORE_PACKAGE_NAME);
	}

	private void setEClassMetadataIsManyValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(metadataSheetDataRow, colIndex, eStructuralFeature.isMany());
	}

	private void setEClassMetadataIsRequiredValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(metadataSheetDataRow, colIndex, eStructuralFeature.isRequired());
	}

	private void setEClassMetadataDefaultValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			if (eAttribute.getDefaultValue() != null) {
				setStringValueCell(metadataSheetDataRow, colIndex,
						EcoreUtil.convertToString(eAttribute.getEAttributeType(), eAttribute.getDefaultValue()));
				return;
			}
		}

		setVoidValueCell(metadataSheetDataRow, colIndex);
	}

	private void setEStructuralFeatureMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
			EStructuralFeature eStructuralFeature) {
		EAnnotation genModelAnnotation = eStructuralFeature.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, genModelAnnotation);
	}

	private void setEEnumMetadataNameValueCell(Range metadataSheetDataRow, int colIndex, EEnumLiteral eEnumLiteral) {
		setStringValueCell(metadataSheetDataRow, colIndex, eEnumLiteral.getName());
	}

	private void setEEnumMetadataLiteralValueCell(Range metadataSheetDataRow, int colIndex, EEnumLiteral eEnumLiteral) {
		setStringValueCell(metadataSheetDataRow, colIndex, eEnumLiteral.getLiteral());
	}

	private void setEEnumMetadataValueValueCell(Range metadataSheetDataRow, int colIndex, EEnumLiteral eEnumLiteral) {
		setNumberValueCell(metadataSheetDataRow, colIndex, eEnumLiteral.getValue());
	}

	private void setEEnumLiteralMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
			EEnumLiteral eEnumLiteral) {
		EAnnotation genModelAnnotation = eEnumLiteral.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, genModelAnnotation);
	}

	private void setMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
			EAnnotation genModelAnnotation) {
		if (genModelAnnotation != null) {
			Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

			if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {
				setMetadataDocumentationValueCell(metadataSheetDataRow, colIndex,
						genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS));
			}
		} else {
			setVoidValueCell(metadataSheetDataRow, colIndex);
		}
	}

	private void setMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex, String documentation) {
		setStringValueCell(metadataSheetDataRow, colIndex, documentation);
	}

	private String getEObjectIdentifier(EObject eObject) {
		return EcoreUtil.getIdentification(eObject);
	}

	private String constructEClassSheetName(EClass eClass) {
		return eClass.getName();
	}

	private String constructEEnumSheetName(EEnum eEnum) {
		return eEnum.getName();
	}

	private String constructEClassMetadataSheetName(EClass eClass) {
		return constructMetadataSheetName(constructEClassSheetName(eClass));
	}

	private String constructEEnumMetadataSheetName(EEnum eEnum) {
		return constructMetadataSheetName(constructEEnumSheetName(eEnum));
	}

	private String constructMetadataSheetName(String metadataSheetName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(metadataSheetName);
		sb.append(" ");
		sb.append("( ");
		sb.append(METADATA_SHEET_SUFFIX);
		sb.append(" )");
		return sb.toString();
	}

	private boolean isProcessed(Set<String> eObjectsIdentifiers, EObject eObject) {
		return eObjectsIdentifiers.contains(getEObjectIdentifier(eObject));
	}

	private List<EObject> safeCopy(List<EObject> eObjects) {
		return EcoreUtil.copyAll(eObjects).stream().collect(toList());
	}
}
