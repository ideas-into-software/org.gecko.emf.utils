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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

import com.github.miachm.sods.Color;
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
	private static final String METADATA_SHEET_SUFFIX = "Metadata";

	private static final String DOCUMENTATION_GENMODEL_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
	private static final String DOCUMENTATION_GENMODEL_DETAILS = "documentation";

	private static final String ECORE_PACKAGE_NAME = "ecore";

	private final static char CR = (char) 0x0D;
	private final static char LF = (char) 0x0A;
	private final static String DATA_CELL_LINE_SEPARATOR = "" + CR + LF;

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

				SpreadSheet document = new SpreadSheet();

				// maps sheet names to instances of sheets
				final Map<String, Sheet> eClassesSheets = new HashMap<String, Sheet>();

				// maps EObjects' unique identifiers (obtained from their hash codes) to
				// instances of sheets, so those can be looked up e.g. when constructing links
				final Map<Integer, Sheet> eObjectsSheets = new HashMap<Integer, Sheet>();

				// stores EObjects' EClasses - used e.g. to construct meta data
				final Set<EClass> eObjectsClasses = new HashSet<EClass>();

				// stores EEnums - used e.g. to construct meta data
				final Set<EEnum> eObjectsEnums = new HashSet<EEnum>();

				// maps EObjects' unique identifiers to pseudo IDs - for those EObjects which
				// lack id field
				final Map<Integer, String> eObjectsPseudoIDs = new HashMap<Integer, String>();

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
						exportNonContainmentEnabled(exportOptions), 
						exportMetadataEnabled(exportOptions), 
						freezeHeaderRowEnabled(exportOptions),
						adjustColumnWidthEnabled(exportOptions),
						eObjectsSafeCopy);
				// @formatter:on

				// @formatter:off
				createSheetsData(document, 
						eClassesSheets, 
						eObjectsClasses, 
						eObjectsEnums,
						eObjectsPseudoIDs, 
						eObjectsSheets,
						exportNonContainmentEnabled(exportOptions), 
						exportMetadataEnabled(exportOptions), 
						generateLinksEnabled(exportOptions),
						eObjectsSafeCopy);
				// @formatter:on

				if (exportMetadataEnabled(exportOptions)) {
					exportMetadata(document, eObjectsClasses, eObjectsEnums);
				}

				document.save(outputStream);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void generatePseudoIDs(List<EObject> eObjects, Map<Integer, String> eObjectsPseudoIDs) {
		final Set<Integer> processedEObjectsIdentifiers = new HashSet<Integer>();

		generatePseudoIDs(eObjects, processedEObjectsIdentifiers, eObjectsPseudoIDs);
	}

	private void generatePseudoIDs(List<EObject> eObjects, Set<Integer> processedEObjectsIdentifiers,
			Map<Integer, String> eObjectsPseudoIDs) {
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
	private void generatePseudoID(EObject eObject, EReference eReference, Set<Integer> processedEObjectsIdentifiers,
			Map<Integer, String> eObjectsPseudoIDs) {
		Object value = eObject.eGet(eReference);
		if (value != null) {
			if (!eReference.isMany() && value instanceof EObject) {
				generatePseudoID((EObject) value, eObjectsPseudoIDs);
			} else if (eReference.isMany()) {
				generatePseudoIDs((List<EObject>) value, processedEObjectsIdentifiers, eObjectsPseudoIDs);
			}
		}
	}

	private void generatePseudoID(EObject eObject, Map<Integer, String> eObjectsPseudoIDs) {
		if (!hasID(eObject, eObjectsPseudoIDs)) {
			eObjectsPseudoIDs.put(getEObjectIdentifier(eObject), UUID.randomUUID().toString());
		}
	}

	private boolean hasID(EObject eObject, Map<Integer, String> eObjectsPseudoIDs) {
		return (hasID(eObject) || hasPseudoID(eObject, eObjectsPseudoIDs));
	}

	private boolean hasID(EObject eObject) {
		return (getID(eObject) != null);
	}

	private String getID(EObject eObject) {
		return EcoreUtil.getID(eObject);
	}

	private boolean hasPseudoID(EObject eObject, Map<Integer, String> eObjectsPseudoIDs) {
		return (eObjectsPseudoIDs.containsKey(getEObjectIdentifier(eObject)));
	}

	private String getPseudoID(EObject eObject, Map<Integer, String> eObjectsPseudoIDs) {
		return eObjectsPseudoIDs.get(getEObjectIdentifier(eObject));
	}

	private void createSheets(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets,
			boolean exportNonContainment, boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth,
			List<EObject> eObjects) {

		final Set<Integer> processedEObjectsIdentifiers = new HashSet<Integer>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createSheetForEObjectWithEReferences(document, 
					eClassesSheets, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsSheets,
					exportNonContainment,
					exportMetadata, 
					freezeHeaderRow,
					adjustColumnWidth,
					eObject);
			// @formatter:on
		}
	}

	private void createSheetsData(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets,
			boolean exportNonContainment, boolean exportMetadata, boolean generateLinks, List<EObject> eObjects)
			throws EMFExportException {

		final Set<Integer> processedEObjectsIdentifiers = new HashSet<Integer>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createSheetDataForEObjectWithEReferences(document, 
					eClassesSheets, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsSheets,
					exportNonContainment,
					exportMetadata, 
					generateLinks,
					eObject);
			// @formatter:on
		}
	}

	private void createSheetForEObjectWithEReferences(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment,
			boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth, EObject eObject) {
		createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums, eObjectsPseudoIDs,
				eObjectsSheets, exportNonContainment, exportMetadata, freezeHeaderRow, adjustColumnWidth, eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			createSheetForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
					eObjectsPseudoIDs, eObjectsSheets, exportNonContainment, exportMetadata, freezeHeaderRow,
					adjustColumnWidth, eObject, r);
		});
	}

	private void createSheetDataForEObjectWithEReferences(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment,
			boolean exportMetadata, boolean generateLinks, EObject eObject) throws EMFExportException {
		createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets,
				exportNonContainment, generateLinks, eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			try {
				createSheetDataForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs,
						eObjectsSheets, exportNonContainment, generateLinks, eObject, r);
			} catch (EMFExportException e) {
				e.printStackTrace();
			}
		});
	}

	private void createSheet(SpreadSheet document, Map<String, Sheet> eClassesSheets, Set<Integer> eObjectsIdentifiers,
			Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment, boolean exportMetadata,
			boolean freezeHeaderRow, boolean adjustColumnWidth, EObject... eObjects) {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Sheet sheet = getOrAddSheet(document, eClassesSheets, eClass, eObjectsEnums,
					hasPseudoID(eObjects[0], eObjectsPseudoIDs), exportMetadata, freezeHeaderRow, adjustColumnWidth);

			for (EObject eObject : eObjects) {
				Integer eObjectIdentifier = Integer.valueOf(getEObjectIdentifier(eObject));

				eObjectsIdentifiers.add(eObjectIdentifier);
				eObjectsClasses.add(eObject.eClass());
				eObjectsSheets.put(eObjectIdentifier, sheet);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					createSheetForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses,
							eObjectsEnums, eObjectsPseudoIDs, eObjectsSheets, exportNonContainment, exportMetadata,
							freezeHeaderRow, adjustColumnWidth, eObject, r);
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createSheetForEReference(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment,
			boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth, EObject eObject, EReference r) {
		if (!exportNonContainment && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
						eObjectsPseudoIDs, eObjectsSheets, exportNonContainment, exportMetadata, freezeHeaderRow,
						adjustColumnWidth, (EObject) value);
			} else if (r.isMany()) {
				createSheet(document, eClassesSheets, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
						eObjectsPseudoIDs, eObjectsSheets, exportNonContainment, exportMetadata, freezeHeaderRow,
						adjustColumnWidth, ((List<EObject>) value).toArray(EObject[]::new));
			}
		}
	}

	private void createSheetData(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<Integer> eObjectsIdentifiers, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment, boolean generateLinks,
			EObject... eObjects) throws EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Sheet sheet = getSheet(eClassesSheets, eClass);

			for (EObject eObject : eObjects) {
				eObjectsIdentifiers.add(getEObjectIdentifier(eObject));

				createSheetData(sheet, eObject, eObjectsPseudoIDs, eObjectsSheets, generateLinks);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					try {
						createSheetDataForEReference(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs,
								eObjectsSheets, exportNonContainment, generateLinks, eObject, r);
					} catch (EMFExportException e) {
						e.printStackTrace();
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createSheetDataForEReference(SpreadSheet document, Map<String, Sheet> eClassesSheets,
			Set<Integer> eObjectsIdentifiers, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Sheet> eObjectsSheets, boolean exportNonContainment, boolean generateLinks, EObject eObject,
			EReference r) throws EMFExportException {
		if (!exportNonContainment && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets,
						exportNonContainment, generateLinks, (EObject) value);
			} else if (r.isMany()) {
				createSheetData(document, eClassesSheets, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsSheets,
						exportNonContainment, generateLinks, ((List<EObject>) value).toArray(EObject[]::new));
			}
		}
	}

	private void createSheetData(Sheet sheet, EObject eObject, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Sheet> eObjectsSheets, boolean generateLinks) {

		sheet.appendRow();

		Range sheetDataRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

		List<EStructuralFeature> eAllStructuralFeatures = eObject.eClass().getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {

			createSheetDataCell(sheetDataRow, colIndex, eObject, eAllStructuralFeatures.get(colIndex),
					eObjectsPseudoIDs, eObjectsSheets, generateLinks);
		}

		if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			setStringValueCell(sheetDataRow, columnsCount, eObjectsPseudoIDs.get(getEObjectIdentifier(eObject)));
		}
	}

	private Sheet getOrAddSheet(SpreadSheet document, Map<String, Sheet> eClassesSheets, EClass eClass,
			Set<EEnum> eObjectsEnums, boolean hasPseudoID, boolean exportMetadata, boolean freezeHeaderRow,
			boolean adjustColumnWidth) {
		String tableName = constructEClassSheetName(eClass);

		boolean tableExists = eClassesSheets.containsKey(tableName);
		Sheet sheet;
		if (tableExists) {
			sheet = eClassesSheets.get(tableName);
		} else {
			sheet = new Sheet(tableName);
			document.appendSheet(sheet);
			eClassesSheets.put(tableName, sheet);

			if (exportMetadata) {
				addMetadataSheet(document, eClass);
			}
		}

		if (!tableExists) {
			createSheetHeader(sheet, eClass, eObjectsEnums, hasPseudoID, adjustColumnWidth);

			if (freezeHeaderRow) {
				// TODO: freezing rows is currently not supported in SODS
				// freezeTableHeader(document, sheet, 1, headersCount);
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
			boolean adjustColumnWidth) {

		List<EStructuralFeature> eAllStructuralFeatures = eClass.getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		sheet.appendColumns((hasPseudoID ? (columnsCount + 1) : columnsCount) - 1); // newly created sheet already has
																					// one column

		Range sheetHeaderRow = sheet.getRange(0, 0, 1, sheet.getMaxColumns()); // newly created sheet already has one
																				// row
		sheetHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {

			EStructuralFeature eStructuralFeature = eAllStructuralFeatures.get(colIndex);

			if (isEcoreEEnumDataType(eStructuralFeature)) {
				eObjectsEnums.add(extractEEnumDataType(eStructuralFeature));
			}

			createSheetHeaderCell(sheetHeaderRow, eStructuralFeature, adjustColumnWidth, colIndex);
		}

		if (hasPseudoID) {
			createSheetHeaderCell(sheetHeaderRow, "id", adjustColumnWidth, columnsCount);
		}
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, EStructuralFeature eStructuralFeature,
			boolean adjustColumnWidth, int colIndex) {
		String tableHeaderName = constructSheetHeaderName(eStructuralFeature);

		createSheetHeaderCell(sheetHeaderRow, tableHeaderName, adjustColumnWidth, colIndex);
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, String tableHeaderName, boolean adjustColumnWidth,
			int colIndex) {
		createSheetHeaderCell(sheetHeaderRow, colIndex, tableHeaderName);

		if (adjustColumnWidth) {
			adjustColumnWidth(sheetHeaderRow.getSheet(), colIndex, tableHeaderName.length());
		}
	}

	private void createSheetHeaderCell(Range sheetHeaderRow, int colIndex, String tableHeaderName) {
		sheetHeaderRow.getCell(0, colIndex).setValue(tableHeaderName);
	}

	private void adjustColumnWidth(Sheet sheet, int colIndex, int charsCount) {
		sheet.setColumnWidth(colIndex, calculateColumnWidth(charsCount));
	}

	private Double calculateColumnWidth(int charsCount) {
		return (Double.valueOf(charsCount) * (charsCount > 3 ? 5 : 10));
	}

	private void adjustRowHeight(Sheet sheet, int rowIndex, String value) {
		sheet.setRowHeight(rowIndex, calculateRowHeight(value));
	}

	private Double calculateRowHeight(String value) {
		return (Double.valueOf(value.length() / MAX_CHAR_PER_LINE_DEFAULT) * 5);
	}

	@Deprecated
	private void freezeSheetHeaderRow(Sheet sheet, int rowCount, int colCount) {
		// TODO: freezing rows is currently not supported in SODS
	}

	private String constructSheetHeaderName(EStructuralFeature eStructuralFeature) {
		StringBuilder sb = new StringBuilder(100);
		if (eStructuralFeature instanceof EReference) {
			sb.append("ref_");
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

	private void createSheetDataCell(Range sheetDataRow, int colIndex, EObject eObject,
			EStructuralFeature eStructuralFeature, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Sheet> eObjectsSheets, boolean generateLinks) {
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

			setEReferenceValueCell(sheetDataRow, colIndex, eObject, eReference, eObjectsPseudoIDs, eObjectsSheets,
					generateLinks);
		}
	}

	@SuppressWarnings("unchecked")
	private void setEReferenceValueCell(Range sheetDataRow, int colIndex, EObject eObject, EReference r,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean generateLinks) {
		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				setOneEReferenceValueCell(sheetDataRow, colIndex, (EObject) value, eObjectsPseudoIDs, eObjectsSheets,
						generateLinks);
			} else if (r.isMany()) {
				setManyEReferencesValueCell(sheetDataRow, colIndex, ((List<EObject>) value), eObjectsPseudoIDs,
						eObjectsSheets, generateLinks);
			}
		}
	}

	private void setOneEReferenceValueCell(Range sheetDataRow, int colIndex, EObject eObject,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean generateLinks) {
		Integer eObjectIdentifier = Integer.valueOf(getEObjectIdentifier(eObject));

		if (hasID(eObject)) {
			if (generateLinks && eObjectsSheets.containsKey(eObjectIdentifier)) {
				setLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getID(eObject),
						eObjectsSheets.get(eObjectIdentifier));
			} else {
				setNonLinkedIDEReferenceValueCell(sheetDataRow, colIndex, getID(eObject));
			}
		} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			if (generateLinks && eObjectsSheets.containsKey(eObjectIdentifier)) {
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
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Sheet> eObjectsSheets, boolean generateLinks) {

		StringBuilder sb = new StringBuilder();

		for (EObject eObject : eObjects) {
			if (hasID(eObject)) {
				sb.append(getID(eObject));
				sb.append(DATA_CELL_LINE_SEPARATOR); // TODO: this has no effect - all lines are output on same line;
														// must be taken care of on SODS side
			} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
				sb.append(getPseudoID(eObject, eObjectsPseudoIDs));
				sb.append(DATA_CELL_LINE_SEPARATOR);
			} else {
				sb.append("EReference: " + eObject.eClass().getName());
				sb.append(DATA_CELL_LINE_SEPARATOR);
			}
		}

		setStringValueCell(sheetDataRow, colIndex, sb.toString());
	}

	private void setLinkedIDEReferenceValueCell(Range sheetDataRow, int colIndex, String refId, Sheet refSheet) {
		setNonLinkedIDEReferenceValueCell(sheetDataRow, colIndex, refId); // FIXME: links are not supported yet in SODS
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

		for (Object value : values) {
			sb.append(EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
			sb.append(DATA_CELL_LINE_SEPARATOR);
		}

		setStringValueCell(sheetDataRow, colIndex, sb.toString());
	}

	private void setVoidValueCell(Range sheetDataRow, int colIndex) {
		sheetDataRow.getCell(0, colIndex).clear();
	}

	private Locale locale(Map<Object, Object> exportOptions) {
		return ((Locale) exportOptions.getOrDefault(EMFExportOptions.OPTION_LOCALE, Locale.getDefault()));
	}

	private boolean exportNonContainmentEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, Boolean.FALSE));
	}

	private boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.FALSE));
	}

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.FALSE));
	}

	private boolean freezeHeaderRowEnabled(Map<Object, Object> exportOptions) {
		// TODO: freezing rows is currently not supported in SODS
		//	return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_FREEZE_HEADER_ROW, Boolean.FALSE));
		return false;
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		// TODO: linking is currently not supported in SODS
		// return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
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

	private void exportMetadata(SpreadSheet document, Set<EClass> eClasses, Set<EEnum> eEnums) {
		exportEClassesMetadata(document, eClasses);
		exportEEnumsMetadata(document, eEnums);
	}

	private void exportEClassesMetadata(SpreadSheet document, Set<EClass> eClasses) {
		for (EClass eClass : eClasses) {

			Sheet metadataSheet = document.getSheet(constructEClassMetadataSheetName(eClass));

			createEClassMetadataSheetHeader(metadataSheet);

			createEClassMetadataSheetData(metadataSheet, eClass);
		}
	}

	private void exportEEnumsMetadata(SpreadSheet document, Set<EEnum> eEnums) {
		for (EEnum eEnum : eEnums) {

			Sheet metadataSheet = addMetadataSheet(document, eEnum);

			createEEnumMetadataSheetHeader(metadataSheet);

			createEEnumMetadataSheetData(metadataSheet, eEnum);
		}
	}

	private void createEClassMetadataSheetHeader(Sheet metadataSheet) {
		createMetadataSheetHeader(metadataSheet, METADATA_ECLASS_SHEET_HEADERS);
	}

	private void createEEnumMetadataSheetHeader(Sheet metadataSheet) {
		createMetadataSheetHeader(metadataSheet, METADATA_EENUM_SHEET_HEADERS);
	}

	private void createMetadataSheetHeader(Sheet metadataSheet, List<String> headers) {
		int columnsCount = headers.size();

		metadataSheet.appendColumns(columnsCount - 1);

		Range metadataSheetHeaderRow = metadataSheet.getRange(0, 0, 1, metadataSheet.getMaxColumns());
		metadataSheetHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < metadataSheet.getMaxColumns(); colIndex++) {
			createMetadataSheetHeaderCell(metadataSheetHeaderRow, colIndex, headers.get(colIndex));
		}
	}

	private void createMetadataSheetHeaderCell(Range metadataSheetHeaderRow, int colIndex, String headerName) {
		metadataSheetHeaderRow.getCell(0, colIndex).setValue(headerName);
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
			setEClassMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, eStructuralFeature);
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
			setEEnumMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, eEnumLiteral);
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

	private void setEClassMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
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

	private void setEEnumMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
			EEnumLiteral eEnumLiteral) {
		EAnnotation genModelAnnotation = eEnumLiteral.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(metadataSheetDataRow, colIndex, genModelAnnotation);
	}

	private void setMetadataDocumentationValueCell(Range metadataSheetDataRow, int colIndex,
			EAnnotation genModelAnnotation) {
		if (genModelAnnotation != null) {
			Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

			if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {
				String documentation = genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS);
				setStringValueCell(metadataSheetDataRow, colIndex, documentation);
			}
		}

		setVoidValueCell(metadataSheetDataRow, colIndex);
	}

	private int getEObjectIdentifier(EObject eObject) {
		return eObject.hashCode();
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

	private String constructMetadataSheetName(String metadataTableName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(metadataTableName);
		sb.append(" ");
		sb.append("( ");
		sb.append(METADATA_SHEET_SUFFIX);
		sb.append(" )");
		return sb.toString();
	}

	private boolean isProcessed(Set<Integer> eObjectsIdentifiers, EObject eObject) {
		return eObjectsIdentifiers.contains(getEObjectIdentifier(eObject));
	}

	private List<EObject> safeCopy(List<EObject> eObjects) {
		return EcoreUtil.copyAll(eObjects).stream().collect(toList());
	}
}
