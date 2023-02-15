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

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.text.StringEscapeUtils;
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

import com.github.jferard.fastods.AnonymousOdsFileWriter;
import com.github.jferard.fastods.FastOdsException;
import com.github.jferard.fastods.OdsDocument;
import com.github.jferard.fastods.OdsFactory;
import com.github.jferard.fastods.Table;
import com.github.jferard.fastods.TableCellWalker;
import com.github.jferard.fastods.Text;
import com.github.jferard.fastods.TextBuilder;
import com.github.jferard.fastods.attribute.BorderStyle;
import com.github.jferard.fastods.attribute.CellAlign;
import com.github.jferard.fastods.attribute.SimpleColor;
import com.github.jferard.fastods.attribute.SimpleLength;
import com.github.jferard.fastods.style.TableCellStyle;
import com.github.jferard.fastods.style.TableColumnStyle;
import com.github.jferard.fastods.style.TableRowStyle;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to ODS format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFODSExporter", scope = ServiceScope.PROTOTYPE)
public class EMFODSExporter implements EMFExporter {
	
	private static final int MAX_CHAR_PER_LINE_DEFAULT = 30;
	
	// @formatter:off
	private static final TableCellStyle HEADER_CELL_STYLE = TableCellStyle.builder("header-cell-style")
			.backgroundColor(SimpleColor.GRAY64)
			.fontWeightBold()
			.build();
	// @formatter:on

	// @formatter:off
	public static final TableCellStyle DEFAULT_BODY_CELL_STYLE = TableCellStyle.builder("default-body-cell-style")
			.borderAll(SimpleLength.mm(0.5), SimpleColor.BLUE, BorderStyle.OUTSET)
			.fontColor(SimpleColor.BLACK)
			.textAlign(CellAlign.CENTER)
			.fontWeightNormal()
			.build();
	// @formatter:on

	// @formatter:off
	public static final TableRowStyle BODY_ROW_STYLE = TableRowStyle.builder("body-row-style")
    		.defaultCellStyle(DEFAULT_BODY_CELL_STYLE)
    		.optimalHeight()
    		.build();
	// @formatter:on
		
	// @formatter:off
	public static final TableColumnStyle BODY_COLUMN_STYLE = TableColumnStyle.builder("body-column-style")
			.optimalWidth() // see: https://github.com/jferard/fastods/wiki/Tutorial#rows-and-columns-styles (...) There's an optimal height/width in the OpenDocument specification (20.383 and 20.384) but LO does not understand it, and FastODS won't compute this optimal value from the cell contents. (...)
			.build();
	// @formatter:on
	
	private static final List<String> METADATA_ECLASS_TABLE_HEADERS = List.of("Name", "Type", "isMany", "isRequired",
			"Default value", "Documentation");
	private static final List<String> METADATA_EENUM_TABLE_HEADERS = List.of("Name", "Literal", "Value",
			"Documentation");
	private static final String METADATA_TABLE_SUFFIX = "Metadata";

	private static final String DOCUMENTATION_GENMODEL_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
	private static final String DOCUMENTATION_GENMODEL_DETAILS = "documentation";
	
	private static final String ECORE_PACKAGE_NAME = "ecore";

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

				final OdsFactory odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("EMFODSExporter"),
						locale(exportOptions));

				final AnonymousOdsFileWriter writer = odsFactory.createWriter();

				final OdsDocument document = writer.document();

				// maps table names to instances of tables
				final Map<String, Table> eClassesTables = new HashMap<String, Table>();

				// maps EObjects' unique identifiers (obtained from their hash codes) to instances of tables, so those can be looked up e.g. when constructing links
				final Map<Integer, Table> eObjectsTables = new HashMap<Integer, Table>();

				// stores EObjects' EClasses - used e.g. to construct meta data
				final Set<EClass> eObjectsClasses = new HashSet<EClass>();

				// stores EEnums - used e.g. to construct meta data
				final Set<EEnum> eObjectsEnums = new HashSet<EEnum>();

				// maps EObjects' unique identifiers to pseudo IDs - for those EObjects which lack id field
				final Map<Integer, String> eObjectsPseudoIDs = new HashMap<Integer, String>();

				final List<EObject> eObjectsSafeCopy = safeCopy(eObjects);

				// pseudo IDs are needed before main processing starts
				generatePseudoIDs(eObjectsSafeCopy, eObjectsPseudoIDs);

				// @formatter:off
				createTables(document, 
						eClassesTables, 
						eObjectsClasses, 
						eObjectsEnums,
						eObjectsPseudoIDs, 
						eObjectsTables,
						exportNonContainmentEnabled(exportOptions), 
						exportMetadataEnabled(exportOptions), 
						freezeHeaderRowEnabled(exportOptions),
						adjustColumnWidthEnabled(exportOptions),
						eObjectsSafeCopy);
				// @formatter:on

				// @formatter:off
				createTablesBodies(document, 
						eClassesTables, 
						eObjectsClasses, 
						eObjectsEnums,
						eObjectsPseudoIDs, 
						eObjectsTables,
						exportNonContainmentEnabled(exportOptions), 
						exportMetadataEnabled(exportOptions), 
						generateLinksEnabled(exportOptions),
						eObjectsSafeCopy);
				// @formatter:on

				if (exportMetadataEnabled(exportOptions)) {
					exportMetadata(document, eObjectsClasses, eObjectsEnums);
				}

				writer.save(outputStream);

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

	private void createTables(OdsDocument document, Map<String, Table> eClassesTables, Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables,
			boolean exportNonContainment, boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth,
			List<EObject> eObjects) throws IOException {

		final Set<Integer> processedEObjectsIdentifiers = new HashSet<Integer>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createTableForEObjectWithEReferences(document, 
					eClassesTables, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsTables,
					exportNonContainment,
					exportMetadata, 
					freezeHeaderRow,
					adjustColumnWidth,
					eObject);
			// @formatter:on
		}
	}

	private void createTablesBodies(OdsDocument document, Map<String, Table> eClassesTables,
			Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Table> eObjectsTables, boolean exportNonContainment, boolean exportMetadata,
			boolean generateLinks, List<EObject> eObjects) throws IOException, EMFExportException {

		final Set<Integer> processedEObjectsIdentifiers = new HashSet<Integer>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			createTableBodyForEObjectWithEReferences(document, 
					eClassesTables, 
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs,
					eObjectsTables,
					exportNonContainment,
					exportMetadata, 
					generateLinks,
					eObject);
			// @formatter:on
		}
	}
	
	private void createTableForEObjectWithEReferences(OdsDocument document, Map<String, Table> eClassesTables,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean exportNonContainment,
			boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth, EObject eObject)
			throws IOException {
		createTable(document, eClassesTables, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums, eObjectsPseudoIDs,
				eObjectsTables, exportNonContainment, exportMetadata, freezeHeaderRow, adjustColumnWidth, eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			createTableForEReference(document, eClassesTables, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
					eObjectsPseudoIDs, eObjectsTables, exportNonContainment, exportMetadata, freezeHeaderRow,
					adjustColumnWidth, eObject, r);
		});
	}

	private void createTableBodyForEObjectWithEReferences(OdsDocument document, Map<String, Table> eClassesTables,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean exportNonContainment,
			boolean exportMetadata, boolean generateLinks, EObject eObject) throws IOException, EMFExportException {
		createTableBody(document, eClassesTables, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsTables,
				exportNonContainment, generateLinks, eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			try {
				createTableBodyForEReference(document, eClassesTables, eObjectsIdentifiers, eObjectsPseudoIDs,
						eObjectsTables, exportNonContainment, generateLinks, eObject, r);
			} catch (EMFExportException e) {
				e.printStackTrace();
			}
		});
	}

	private void createTable(OdsDocument document, Map<String, Table> eClassesTables, Set<Integer> eObjectsIdentifiers,
			Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Table> eObjectsTables, boolean exportNonContainment, boolean exportMetadata,
			boolean freezeHeaderRow, boolean adjustColumnWidth, EObject... eObjects) throws IOException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Table table = getOrAddTable(document, eClassesTables, eClass, eObjectsEnums,
					hasPseudoID(eObjects[0], eObjectsPseudoIDs), exportMetadata, freezeHeaderRow, adjustColumnWidth);

			for (EObject eObject : eObjects) {
				Integer eObjectIdentifier = Integer.valueOf(getEObjectIdentifier(eObject));

				eObjectsIdentifiers.add(eObjectIdentifier);
				eObjectsClasses.add(eObject.eClass());
				eObjectsTables.put(eObjectIdentifier, table);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					createTableForEReference(document, eClassesTables, eObjectsIdentifiers, eObjectsClasses,
							eObjectsEnums, eObjectsPseudoIDs, eObjectsTables, exportNonContainment, exportMetadata,
							freezeHeaderRow, adjustColumnWidth, eObject, r);
				});
			}
		}
	}

	private void createTableBody(OdsDocument document, Map<String, Table> eClassesTables,
			Set<Integer> eObjectsIdentifiers, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Table> eObjectsTables, boolean exportNonContainment, boolean generateLinks,
			EObject... eObjects) throws IOException, EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Table table = getTable(eClassesTables, eClass);

			final TableCellWalker walker = table.getWalker();

			walker.lastRow();

			for (EObject eObject : eObjects) {
				eObjectsIdentifiers.add(getEObjectIdentifier(eObject));

				createTableBody(walker, eObject, eObjectsPseudoIDs, eObjectsTables, generateLinks);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					try {
						createTableBodyForEReference(document, eClassesTables, eObjectsIdentifiers, eObjectsPseudoIDs,
								eObjectsTables, exportNonContainment, generateLinks, eObject, r);
					} catch (EMFExportException e) {
						e.printStackTrace();
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createTableForEReference(OdsDocument document, Map<String, Table> eClassesTables,
			Set<Integer> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean exportNonContainment,
			boolean exportMetadata, boolean freezeHeaderRow, boolean adjustColumnWidth, EObject eObject, EReference r) {
		if (!exportNonContainment && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				try {
					createTable(document, eClassesTables, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
							eObjectsPseudoIDs, eObjectsTables, exportNonContainment, exportMetadata, freezeHeaderRow,
							adjustColumnWidth, (EObject) value);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (r.isMany()) {
				try {
					createTable(document, eClassesTables, eObjectsIdentifiers, eObjectsClasses, eObjectsEnums,
							eObjectsPseudoIDs, eObjectsTables, exportNonContainment, exportMetadata, freezeHeaderRow,
							adjustColumnWidth, ((List<EObject>) value).toArray(EObject[]::new));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void createTableBodyForEReference(OdsDocument document, Map<String, Table> eClassesTables,
			Set<Integer> eObjectsIdentifiers, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Table> eObjectsTables, boolean exportNonContainment, boolean generateLinks, EObject eObject,
			EReference r) throws EMFExportException {
		if (!exportNonContainment && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				try {
					createTableBody(document, eClassesTables, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsTables,
							exportNonContainment, generateLinks, (EObject) value);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (r.isMany()) {
				try {
					createTableBody(document, eClassesTables, eObjectsIdentifiers, eObjectsPseudoIDs, eObjectsTables,
							exportNonContainment, generateLinks, ((List<EObject>) value).toArray(EObject[]::new));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private Table getOrAddTable(OdsDocument document, Map<String, Table> eClassesTables, EClass eClass,
			Set<EEnum> eObjectsEnums, boolean hasPseudoID, boolean exportMetadata, boolean freezeHeaderRow,
			boolean adjustColumnWidth) throws IOException {
		String tableName = constructEClassTableName(eClass);

		boolean tableExists = eClassesTables.containsKey(tableName);
		Table table;
		if (tableExists) {
			table = eClassesTables.get(tableName);
		} else {
			table = document.addTable(tableName);
			eClassesTables.put(tableName, table);

			if (exportMetadata) {
				addMetadataTable(document, eClass);
			}
		}

		final TableCellWalker walker = table.getWalker();

		if (!tableExists) {
			// because com.github.jferard.fastods.TableCellWalker.getColumnCount() returns incorrect values - filed as FastODS bug (https://github.com/jferard/fastods/issues/244)
			int headersCount = createTableHeader(walker, eClass, eObjectsEnums, hasPseudoID, adjustColumnWidth);

			if (freezeHeaderRow) {
				freezeTableHeader(document, table, 1, headersCount);
			}
		}

		walker.nextRow();

		return table;
	}
	
	private void freezeTableHeader(OdsDocument document, Table table, int rowCount, int colCount) {
		// FIXME: does not produce expected result, i.e. freezed header row - filed as FastODS bug (https://github.com/jferard/fastods/issues/143)
		document.freezeCells(table, rowCount, colCount);
	}

	private Table getTable(Map<String, Table> eClassesTables, EClass eClass) throws EMFExportException {
		String tableName = constructEClassTableName(eClass);

		if (!eClassesTables.containsKey(tableName)) {
			throw new EMFExportException("Table '" + tableName + "' does not exist!");
		}

		return eClassesTables.get(tableName);
	}

	private void addMetadataTable(OdsDocument document, EClass eClass) throws IOException {
		document.addTable(constructEClassMetadataTableName(eClass));
	}
	
	private int createTableHeader(TableCellWalker walker, EClass eClass, Set<EEnum> eObjectsEnums, boolean hasPseudoID,
			boolean adjustColumnWidth) throws IOException {
		walker.setRowStyle(BODY_ROW_STYLE);
		if (!adjustColumnWidth) {
			walker.setColumnStyle(BODY_COLUMN_STYLE);
		}

		AtomicInteger headersCount = new AtomicInteger(0);

		AtomicInteger eStructuralFeaturesCount = new AtomicInteger(eClass.getEAllStructuralFeatures().size());

		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			if (isEcoreEEnumDataType(eStructuralFeature)) {
				eObjectsEnums.add(extractEEnumDataType(eStructuralFeature));
			}

			createTableHeaderCell(walker, eStructuralFeature, adjustColumnWidth, headersCount.get());

			// because com.github.jferard.fastods.TableCellWalker.getColumnCount() returns incorrect values - filed as FastODS bug (https://github.com/jferard/fastods/issues/244)
			int currentHeadersCount = headersCount.incrementAndGet();

			boolean hasNext = (currentHeadersCount < eStructuralFeaturesCount.get());

			if (hasNext) {
				walker.next();
			}
		});

		if (hasPseudoID) {
			walker.next();

			createTableHeaderCell(walker, "id", adjustColumnWidth, headersCount.get(), 36);

			headersCount.getAndIncrement();
		}

		return headersCount.get();
	}	
	
	private void createTableHeaderCell(TableCellWalker walker, EStructuralFeature eStructuralFeature,
			boolean adjustColumnWidth, int colIndex) {
		String tableHeaderName = constructTableHeaderName(eStructuralFeature);

		createTableHeaderCell(walker, tableHeaderName);

		if (adjustColumnWidth) {
			adjustColumnWidth(walker, colIndex, tableHeaderName.length());
		}
	}

	private void createTableHeaderCell(TableCellWalker walker, String tableHeaderName, boolean adjustColumnWidth,
			int colIndex, int charsCount) {
		createTableHeaderCell(walker, tableHeaderName);

		if (adjustColumnWidth) {
			adjustColumnWidth(walker, colIndex, charsCount);
		}
	}
	
	private void adjustColumnWidth(TableCellWalker walker, int colIndex, int charsCount) {
		TableColumnStyle adjustedWithColumnStyle = constructAdjustedWithColumnStyle(charsCount);
		
		// FIXME: neither of these produce expected result, i.e. varied column width - filed as FastODS bug (https://github.com/jferard/fastods/issues/243)
	//	walker.setColumnStyle(adjustedWithColumnStyle); 
		walker.getTable().setColumnStyle(colIndex, adjustedWithColumnStyle);
	}	
	
	private TableColumnStyle constructAdjustedWithColumnStyle(int charsCount) {
		// @formatter:off
		return TableColumnStyle.builder("adjusted-with-column-style")
				.columnWidth(calculateColumnWidth(charsCount))
				.build();
		// @formatter:on
	}
	
	private SimpleLength calculateColumnWidth(int charsCount) {
		SimpleLength columnWidth = SimpleLength.mm(charsCount * 10);

		return columnWidth;
	}

	private void createTableHeaderCell(TableCellWalker walker, String tableHeaderName) {
		walker.setStringValue(tableHeaderName);
		walker.setStyle(HEADER_CELL_STYLE);
	}

	private String constructTableHeaderName(EStructuralFeature eStructuralFeature) {
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

	private void createTableBody(TableCellWalker walker, EObject eObject, Map<Integer, String> eObjectsPseudoIDs,
			Map<Integer, Table> eObjectsTables, boolean generateLinks) throws IOException {
		eObject.eClass().getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			walker.setRowStyle(BODY_ROW_STYLE);
			walker.setColumnStyle(BODY_COLUMN_STYLE);

			createTableBodyCell(walker, eObject, eStructuralFeature, eObjectsPseudoIDs, eObjectsTables, generateLinks);

			walker.next();
		});

		if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			setStringValueCell(walker, eObjectsPseudoIDs.get(getEObjectIdentifier(eObject)));

			walker.next();
		}

		walker.nextRow();
	}

	private void createTableBodyCell(TableCellWalker walker, EObject eObject, EStructuralFeature eStructuralFeature,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean generateLinks) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			Object value = eObject.eGet(eAttribute);
			if (value != null) {
				if (!eAttribute.isMany()) {

					if (value instanceof Date) {
						setDateValueCell(walker, (Date) value);

					} else if (value instanceof Number) {
						setNumberValueCell(walker, (Number) value);

					} else if (value instanceof Boolean) {
						setBooleanValueCell(walker, (Boolean) value);

					} else if (value instanceof byte[]) {
						// TODO: clarify how byte arrays should be handled
						walker.setStringValue("EAttribute: byte[]");

					} else {
						setStringValueCell(walker, EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
					}

				} else {
					setMultiValueCell(walker, eAttribute, value);
				}

			} else {
				setVoidValueCell(walker);
			}

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setEReferenceValueCell(walker, eObject, eReference, eObjectsPseudoIDs, eObjectsTables, generateLinks);
		}
	}

	@SuppressWarnings("unchecked")
	private void setEReferenceValueCell(TableCellWalker walker, EObject eObject, EReference r,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean generateLinks) {
		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				setOneEReferenceValueCell(walker, (EObject) value, eObjectsPseudoIDs, eObjectsTables, generateLinks);
			} else if (r.isMany()) {
				setManyEReferencesValueCell(walker, ((List<EObject>) value), eObjectsPseudoIDs, eObjectsTables,
						generateLinks);
			}
		}
	}

	private void setOneEReferenceValueCell(TableCellWalker walker, EObject eObject,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean generateLinks) {
		Integer eObjectIdentifier = Integer.valueOf(getEObjectIdentifier(eObject));

		if (hasID(eObject)) {
			if (generateLinks && eObjectsTables.containsKey(eObjectIdentifier)) {
				setLinkedIDEReferenceValueCell(walker, getID(eObject), eObjectsTables.get(eObjectIdentifier));
			} else {
				setNonLinkedIDEReferenceValueCell(walker, getID(eObject));
			}
		} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
			if (generateLinks && eObjectsTables.containsKey(eObjectIdentifier)) {
				setLinkedIDEReferenceValueCell(walker, getPseudoID(eObject, eObjectsPseudoIDs),
						eObjectsTables.get(eObjectIdentifier));
			} else {
				setNonLinkedIDEReferenceValueCell(walker, getPseudoID(eObject, eObjectsPseudoIDs));
			}
		} else {
			setNoIDEReferenceValueCell(walker, eObject);
		}
	}

	private void setManyEReferencesValueCell(TableCellWalker walker, List<EObject> eObjects,
			Map<Integer, String> eObjectsPseudoIDs, Map<Integer, Table> eObjectsTables, boolean generateLinks) {
		TextBuilder textBuilder = Text.builder();

		for (EObject eObject : eObjects) {
			Integer eObjectIdentifier = Integer.valueOf(getEObjectIdentifier(eObject));

			if (hasID(eObject)) {
				if (generateLinks && eObjectsTables.containsKey(eObjectIdentifier)) {
					textBuilder = setLinkedIDEReferenceValueCell(textBuilder, getID(eObject),
							eObjectsTables.get(eObjectIdentifier));
				} else {
					textBuilder = setNonLinkedIDEReferenceValueCell(textBuilder, getID(eObject));
				}
			} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
				if (generateLinks && eObjectsTables.containsKey(eObjectIdentifier)) {
					textBuilder = setLinkedIDEReferenceValueCell(textBuilder, getPseudoID(eObject, eObjectsPseudoIDs),
							eObjectsTables.get(eObjectIdentifier));
				} else {
					textBuilder = setNonLinkedIDEReferenceValueCell(textBuilder,
							getPseudoID(eObject, eObjectsPseudoIDs));
				}
			} else {
				setNoIDEReferenceValueCell(walker, eObject);
			}
		}

		Text text = textBuilder.build();
		walker.setText(text);

		// adjust row height based on number of EObjects
		adjustRowHeight(walker, eObjects.size());
	}

	private void setLinkedIDEReferenceValueCell(TableCellWalker walker, String refId, Table refTable) {
		walker.setText(setLinkedIDEReferenceValueCell(Text.builder(), refId, refTable).build());
	}

	private TextBuilder setLinkedIDEReferenceValueCell(TextBuilder textBuilder, String refId, Table refTable) {
		return textBuilder.par().link(refId, refTable);
	}

	private void setNonLinkedIDEReferenceValueCell(TableCellWalker walker, String refId) {
		walker.setText(setNonLinkedIDEReferenceValueCell(Text.builder(), refId).build());
	}

	private TextBuilder setNonLinkedIDEReferenceValueCell(TextBuilder textBuilder, String refId) {
		return textBuilder.parContent(refId);
	}

	private void setNoIDEReferenceValueCell(TableCellWalker walker, EObject eObject) {
		walker.setStringValue("EReference: " + eObject.eClass().getName());
	}

	private void setStringValueCell(TableCellWalker walker, String value) {
		if (value.length() <= MAX_CHAR_PER_LINE_DEFAULT) {
			walker.setStringValue(value);
		} else {
			setMultilineTextCell(walker, value);
		}
	}

	private void setMultilineTextCell(TableCellWalker walker, String value) {
		String wrapped = WordUtils.wrap(value, MAX_CHAR_PER_LINE_DEFAULT, System.lineSeparator(), false);

		String[] pieces = wrapped.split(System.lineSeparator());

		TextBuilder textBuilder = Text.builder();
		for (String piece : pieces) {
			textBuilder = textBuilder.parContent(StringEscapeUtils.escapeXml11(piece));
		}
		Text text = textBuilder.build();
		walker.setText(text);

		// adjust row height based on number of new lines
		adjustRowHeight(walker, pieces.length);
	}

	private void setDateValueCell(TableCellWalker walker, Date value) {
		walker.setDateValue(value);
	}

	private void setNumberValueCell(TableCellWalker walker, Number value) {
		walker.setFloatValue(value.floatValue());
	}

	private void setBooleanValueCell(TableCellWalker walker, Boolean value) {
		walker.setBooleanValue(value.booleanValue());
	}

	@SuppressWarnings("unchecked")
	private void setMultiValueCell(TableCellWalker walker, EAttribute eAttribute, Object multiValue) {
		TextBuilder textBuilder = Text.builder();

		Collection<Object> values = (Collection<Object>) multiValue;
		for (Object value : values) {
			textBuilder.parContent(EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
		}

		Text text = textBuilder.build();
		walker.setText(text);
	}

	private void setVoidValueCell(TableCellWalker walker) {
		walker.setVoidValue();
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
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_FREEZE_HEADER_ROW, Boolean.FALSE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}

	private Map<Object, Object> validateExportOptions(Map<?, ?> options) {
		if (options == null) {
			return Collections.emptyMap();
		} else {
			return Map.copyOf(options);
		}
	}

	private void exportMetadata(OdsDocument document, Set<EClass> eClasses, Set<EEnum> eEnums)
			throws IOException, FastOdsException {
		exportEClassesMetadata(document, eClasses);
		exportEEnumsMetadata(document, eEnums);
	}

	private void exportEClassesMetadata(OdsDocument document, Set<EClass> eClasses)
			throws IOException, FastOdsException {
		for (EClass eClass : eClasses) {

			String metadataTableName = constructEClassMetadataTableName(eClass);

			Table metadataTable = document.getTable(metadataTableName);

			final TableCellWalker walker = metadataTable.getWalker();

			createEClassMetadataTableHeader(walker);

			walker.nextRow();

			createEClassMetadataTableBody(walker, eClass);
		}
	}

	private void exportEEnumsMetadata(OdsDocument document, Set<EEnum> eEnums) throws IOException, FastOdsException {
		for (EEnum eEnum : eEnums) {

			String metadataTableName = constructEEnumMetadataTableName(eEnum);

			Table metadataTable = document.addTable(metadataTableName);

			final TableCellWalker walker = metadataTable.getWalker();

			createEEnumMetadataTableHeader(walker);

			walker.nextRow();

			createEEnumMetadataTableBody(walker, eEnum);
		}
	}

	private void createEClassMetadataTableHeader(TableCellWalker walker) throws IOException {
		METADATA_ECLASS_TABLE_HEADERS.forEach(h -> {
			walker.setRowStyle(BODY_ROW_STYLE);
			walker.setColumnStyle(BODY_COLUMN_STYLE);
			walker.setStyle(HEADER_CELL_STYLE);
			walker.setStringValue(h);
			walker.next();
		});
	}

	private void createEEnumMetadataTableHeader(TableCellWalker walker) throws IOException {
		METADATA_EENUM_TABLE_HEADERS.forEach(h -> {
			walker.setRowStyle(BODY_ROW_STYLE);
			walker.setColumnStyle(BODY_COLUMN_STYLE);
			walker.setStyle(HEADER_CELL_STYLE);
			walker.setStringValue(h);
			walker.next();
		});
	}

	private void createEClassMetadataTableBody(TableCellWalker walker, EClass eClass) throws IOException {
		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			createEClassMetadataTableBodyCell(walker, eStructuralFeature);

			try {
				walker.nextRow();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void createEEnumMetadataTableBody(TableCellWalker walker, EEnum eEnum) throws IOException {
		eEnum.getEAnnotations(); // TODO: clarify regarding instance-level docs

		eEnum.getELiterals().forEach(eEnumLiteral -> {

			createEEnumMetadataTableBodyCell(walker, eEnumLiteral);

			try {
				walker.nextRow();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void createEEnumMetadataTableBodyCell(TableCellWalker walker, EEnumLiteral eEnumLiteral) {
		walker.setRowStyle(BODY_ROW_STYLE);
		walker.setColumnStyle(BODY_COLUMN_STYLE);

		// Name
		setEEnumMetadataNameValueCell(walker, eEnumLiteral);

		walker.next();

		// Literal
		setEEnumMetadataLiteralValueCell(walker, eEnumLiteral);

		walker.next();

		// Value
		setEEnumMetadataValueValueCell(walker, eEnumLiteral);

		walker.next();

		// Documentation
		setEEnumMetadataDocumentationValueCell(walker, eEnumLiteral);

		walker.next();
	}

	private void setEEnumMetadataNameValueCell(TableCellWalker walker, EEnumLiteral eEnumLiteral) {
		setStringValueCell(walker, eEnumLiteral.getName());
	}

	private void setEEnumMetadataLiteralValueCell(TableCellWalker walker, EEnumLiteral eEnumLiteral) {
		setStringValueCell(walker, eEnumLiteral.getLiteral());
	}

	private void setEEnumMetadataValueValueCell(TableCellWalker walker, EEnumLiteral eEnumLiteral) {
		setNumberValueCell(walker, eEnumLiteral.getValue());
	}

	private void setEEnumMetadataDocumentationValueCell(TableCellWalker walker, EEnumLiteral eEnumLiteral) {
		EAnnotation genModelAnnotation = eEnumLiteral.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(walker, genModelAnnotation);
	}

	private void createEClassMetadataTableBodyCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		walker.setRowStyle(BODY_ROW_STYLE);
		walker.setColumnStyle(BODY_COLUMN_STYLE);

		// Name
		setEClassMetadataNameValueCell(walker, eStructuralFeature);

		walker.next();

		// Type
		setEClassMetadataTypeValueCell(walker, eStructuralFeature);

		walker.next();

		// isMany
		setEClassMetadataIsManyValueCell(walker, eStructuralFeature);

		walker.next();

		// isRequired
		setEClassMetadataIsRequiredValueCell(walker, eStructuralFeature);

		walker.next();

		// Default value
		setEClassMetadataDefaultValueCell(walker, eStructuralFeature);

		walker.next();

		// Documentation
		setEClassMetadataDocumentationValueCell(walker, eStructuralFeature);

		walker.next();
	}

	private void setEClassMetadataNameValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		setStringValueCell(walker, eStructuralFeature.getName());
	}

	private void setEClassMetadataTypeValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			setStringValueCell(walker, normalizeMetadataTypeEAttributeName(eAttribute.getEAttributeType()));

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setStringValueCell(walker, eReference.getEReferenceType().getName());

		} else {
			setVoidValueCell(walker);
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

	private void setEClassMetadataIsManyValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(walker, eStructuralFeature.isMany());
	}

	private void setEClassMetadataIsRequiredValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(walker, eStructuralFeature.isRequired());
	}

	private void setEClassMetadataDefaultValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			if (eAttribute.getDefaultValue() != null) {
				setStringValueCell(walker,
						EcoreUtil.convertToString(eAttribute.getEAttributeType(), eAttribute.getDefaultValue()));
				return;
			}
		}

		setVoidValueCell(walker);
	}

	private void setEClassMetadataDocumentationValueCell(TableCellWalker walker,
			EStructuralFeature eStructuralFeature) {
		EAnnotation genModelAnnotation = eStructuralFeature.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(walker, genModelAnnotation);
	}

	private void setMetadataDocumentationValueCell(TableCellWalker walker, EAnnotation genModelAnnotation) {
		if (genModelAnnotation != null) {
			Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

			if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {
				String documentation = genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS);
				setStringValueCell(walker, documentation);
			}
		}

		setVoidValueCell(walker);
	}

	private int getEObjectIdentifier(EObject eObject) {
		return eObject.hashCode();
	}

	private String constructEClassTableName(EClass eClass) {
		return eClass.getName();
	}

	private String constructEEnumTableName(EEnum eEnum) {
		return eEnum.getName();
	}

	private String constructEClassMetadataTableName(EClass eClass) {
		return constructMetadataTableName(constructEClassTableName(eClass));
	}

	private String constructEEnumMetadataTableName(EEnum eEnum) {
		return constructMetadataTableName(constructEEnumTableName(eEnum));
	}

	private String constructMetadataTableName(String metadataTableName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(metadataTableName);
		sb.append(" ");
		sb.append("( ");
		sb.append(METADATA_TABLE_SUFFIX);
		sb.append(" )");
		return sb.toString();
	}

	private void adjustRowHeight(TableCellWalker walker, int linesCount) {
		walker.setRowStyle(constructAdjustedHeightRowStyle(linesCount));
	}

	private TableRowStyle constructAdjustedHeightRowStyle(int linesCount) {
		// @formatter:off
		return TableRowStyle.builder("adjusted-height-row-style")
				.defaultCellStyle(DEFAULT_BODY_CELL_STYLE)
				.rowHeight(calculateRowHeight(linesCount))
				.build();
		// @formatter:on
	}

	private SimpleLength calculateRowHeight(int linesCount) {
		return SimpleLength.mm(linesCount * 5);
	}

	private boolean isProcessed(Set<Integer> eObjectsIdentifiers, EObject eObject) {
		return eObjectsIdentifiers.contains(getEObjectIdentifier(eObject));
	}

	private List<EObject> safeCopy(List<EObject> eObjects) {
		return EcoreUtil.copyAll(eObjects).stream().collect(toList());
	}
}
