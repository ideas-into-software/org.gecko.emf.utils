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
import java.util.Set;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
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
import com.github.jferard.fastods.style.TableRowStyle;
import com.google.common.base.Splitter;

@Component(name = "EMFODSExporter", scope = ServiceScope.PROTOTYPE)
public class EMFODSExporter implements EMFExporter {
	
	private static final Integer MAX_CHAR_PER_LINE_DEFAULT = 30;
	
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
    		.rowHeight(SimpleLength.cm(1))
    		.build();
	// @formatter:on
	
	private static final List<String> METADATA_TABLE_HEADERS = List.of("Name", "Type", "isMany", "isRequired", "Default value");
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExporter#exportResourceTo(org.eclipse.emf.ecore.resource.Resource, java.util.Locale, java.io.OutputStream, java.util.Map)
	 */
	@Override
	public void exportResourceTo(Resource t, Locale locale, OutputStream outputStream, Map<?, ?> options)
			throws EMFExportException {
		try {

			final OdsFactory odsFactory = OdsFactory.create(java.util.logging.Logger.getLogger("EMFODSExporter"),
					locale);

			final AnonymousOdsFileWriter writer = odsFactory.createWriter();

			final OdsDocument document = writer.document();

			final Map<String, Table> eObjectsTableMap = new HashMap<String, Table>();

			final Set<Integer> eObjectsIDs = new HashSet<Integer>();

			final Set<EClass> eObjectsClasses = new HashSet<EClass>();

			final Map<Object, Object> exportOptions = validateExportOptions(options);

			for (EObject eObject : t.getContents()) {
				// @formatter:off
				createTableForEObjectWithEReferences(document, 
						eObjectsTableMap, 
						eObjectsIDs, 
						eObjectsClasses, 
						exportNonContainmentEnabled(exportOptions),
						exportMetadataEnabled(exportOptions), 
						eObject);
				// @formatter:on
			}

			if (exportMetadataEnabled(exportOptions)) {
				exportMetadata(document, eObjectsClasses);
			}

			writer.save(outputStream);

		} catch (Exception e) {
			throw new EMFExportException(e);
		}
	}
	
	private void createTableForEObjectWithEReferences(final OdsDocument document,
			final Map<String, Table> eObjectsTableMap, final Set<Integer> eObjectsIDs,
			final Set<EClass> eObjectsClasses, boolean exportNonContainment, boolean exportMetadata, EObject eObject)
			throws IOException {
		createTable(document, eObjectsTableMap, eObjectsIDs, eObjectsClasses, exportNonContainment, exportMetadata,
				eObject);

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			createTableForEReference(document, eObjectsTableMap, eObjectsIDs, eObjectsClasses, exportNonContainment,
					exportMetadata, eObject, r);
		});
	}
	
	private void createTable(final OdsDocument document, final Map<String, Table> eObjectsTableMap,
			final Set<Integer> eObjectsIDs, final Set<EClass> eObjectsClasses, boolean exportNonContainment,
			boolean exportMetadata, EObject... eObjects) throws IOException {
		if ((eObjects.length > 0) && (!eObjectsIDs.contains(getEObjectID(eObjects[0])))) {
			EClass eClass = eObjects[0].eClass();

			Table table = getOrAddTable(document, eObjectsTableMap, eClass, exportMetadata);

			final TableCellWalker walker = table.getWalker();

			walker.lastRow();

			for (EObject eObject : eObjects) {
				eObjectsIDs.add(getEObjectID(eObject));
				eObjectsClasses.add(eObject.eClass());

				createTableBody(document, walker, eObject);

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					createTableForEReference(document, eObjectsTableMap, eObjectsIDs, eObjectsClasses,
							exportNonContainment, exportMetadata, eObject, r);
				});
			}

			// FIXME: freezing header row does not work
			// document.freezeCells(table, 1, 1);
			// document.freezeCells(table, 1, eObject.eClass().getFeatureCount());
		}
	}
	
	@SuppressWarnings("unchecked")
	private void createTableForEReference(final OdsDocument document, final Map<String, Table> eObjectsTableMap,
			final Set<Integer> eObjectsIDs, final Set<EClass> eObjectsClasses, boolean exportNonContainment,
			boolean exportMetadata, EObject eObject, EReference r) {
		if (!exportNonContainment && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {
				try {
					createTable(document, eObjectsTableMap, eObjectsIDs, eObjectsClasses, exportNonContainment,
							exportMetadata, (EObject) value);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (r.isMany()) {
				try {
					createTable(document, eObjectsTableMap, eObjectsIDs, eObjectsClasses, exportNonContainment,
							exportMetadata, ((List<EObject>) value).toArray(EObject[]::new));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private Table getOrAddTable(OdsDocument document, Map<String, Table> eObjectsTableMap, EClass eClass,
			boolean exportMetadata) throws IOException {
		String tableName = constructTableName(eClass);

		boolean tableExists = eObjectsTableMap.containsKey(tableName);
		Table table;
		if (tableExists) {
			table = eObjectsTableMap.get(tableName);
		} else {
			table = document.addTable(tableName);
			eObjectsTableMap.put(tableName, table);

			if (exportMetadata) {
				addMetadataTable(document, eClass);
			}
		}

		final TableCellWalker walker = table.getWalker();

		if (!tableExists) {
			createTableHeader(walker, eClass);
		}

		walker.nextRow();

		return table;
	}
	
	private void addMetadataTable(OdsDocument document, EClass eClass) throws IOException {
		document.addTable(constructMetadataTableName(eClass));
	}
	
	private void createTableHeader(TableCellWalker walker, EClass eClass) throws IOException {
		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			walker.setStringValue(eStructuralFeature.getName());
			walker.setStyle(HEADER_CELL_STYLE);

			walker.next();
		});
	}

	private void createTableBody(final OdsDocument document, TableCellWalker walker, EObject eObject)
			throws IOException {
		eObject.eClass().getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			walker.setRowStyle(BODY_ROW_STYLE);

			createTableBodyCell(walker, eObject, eStructuralFeature);

			walker.next();
		});

		walker.nextRow();
	}

	private void createTableBodyCell(TableCellWalker walker, EObject eObject, EStructuralFeature eStructuralFeature) {
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

			setEReferenceValueCell(walker, eObject, eReference);
		}
	}
	
	private void setEReferenceValueCell(TableCellWalker walker, EObject eObject, EReference r) {
		// TODO: clarify how references should be presented in such case, in addition to
		// being output on separate sheets
		walker.setStringValue("EReference: " + r.getName());
	}

	private void setStringValueCell(TableCellWalker walker, String value) {
		if (value.length() <= MAX_CHAR_PER_LINE_DEFAULT) {
			walker.setStringValue(value);
		} else {
			setMultilineTextCell(walker, value);
		}
	}

	private void setMultilineTextCell(TableCellWalker walker, String value) {
		Iterable<String> pieces = Splitter.fixedLength(30).split(value);
		TextBuilder textBuilder = Text.builder();
		for (String piece : pieces) {
			textBuilder = textBuilder.parContent(StringEscapeUtils.escapeXml11(piece));
		}
		Text text = textBuilder.build();
		walker.setText(text);
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

	private boolean exportNonContainmentEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, Boolean.FALSE));
	}

	private boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.FALSE));
	}

	private Map<Object, Object> validateExportOptions(Map<?, ?> options) {
		if (options == null) {
			return Collections.emptyMap();
		} else {
			return Map.copyOf(options);
		}
	}

	private void exportMetadata(final OdsDocument document, Set<EClass> eClasses) throws IOException, FastOdsException {
		for (EClass eClass : eClasses) {

			String metadataTableName = constructMetadataTableName(eClass);

			Table metadataTable = document.getTable(metadataTableName);

			final TableCellWalker walker = metadataTable.getWalker();

			createMetadataTableHeader(walker, eClass);

			walker.nextRow();

			createMetadataTableBody(walker, eClass);
		}
	}
	
	private void createMetadataTableHeader(TableCellWalker walker, EClass eClass) throws IOException {
		METADATA_TABLE_HEADERS.forEach(h -> {
			walker.setStyle(HEADER_CELL_STYLE);
			walker.setStringValue(h);
			walker.next();
		});
	}

	private void createMetadataTableBody(TableCellWalker walker, EClass eClass) throws IOException {
		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {

			createMetadataTableBodyCell(walker, eStructuralFeature);

			try {
				walker.nextRow();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
	
	private void createMetadataTableBodyCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		walker.setRowStyle(BODY_ROW_STYLE);

		// Name
		setStringValueCell(walker, eStructuralFeature.getName());

		walker.next();

		// Type
		setMetadataTypeValueCell(walker, eStructuralFeature);

		walker.next();

		// isMany
		setBooleanValueCell(walker, eStructuralFeature.isMany());

		walker.next();

		// isRequired
		setBooleanValueCell(walker, eStructuralFeature.isRequired());

		walker.next();

		// Default value
		setMetadataDefaultValueCell(walker, eStructuralFeature);

		walker.next();
	}	
	
	private void setMetadataTypeValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			setStringValueCell(walker, eAttribute.getEAttributeType().getName());

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setStringValueCell(walker, eReference.getEReferenceType().getName());

		} else {
			setVoidValueCell(walker);
		}
	}

	private void setMetadataDefaultValueCell(TableCellWalker walker, EStructuralFeature eStructuralFeature) {
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

	private int getEObjectID(EObject eObject) {
		return eObject.hashCode();
	}

	private String constructTableName(EClass eClass) {
		return eClass.getName();
	}

	private String constructMetadataTableName(EClass eClass) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(constructTableName(eClass));
		sb.append(" ");
		sb.append("( ");
		sb.append("Metadata");
		sb.append(" )");
		return sb.toString();
	}
}
