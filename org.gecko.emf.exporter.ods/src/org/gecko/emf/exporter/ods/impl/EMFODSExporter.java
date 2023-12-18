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
package org.gecko.emf.exporter.ods.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.EMFExporterConstants;
import org.gecko.emf.exporter.cells.EMFExportEObjectIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.cells.EMFExportInternalIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportMappingMatrixReferenceValueCell;
import org.gecko.emf.exporter.ods.api.EMFODSExportOptions;
import org.gecko.emf.exporter.ods.api.EMFODSExporterConstants;
import org.osgi.annotation.bundle.Capability;
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
import com.google.common.base.Stopwatch;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting
 * EMF resources and lists of EMF objects to ODS format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = EMFODSExporterConstants.EMF_EXPORTER_NAME, scope = ServiceScope.PROTOTYPE)
@Capability(namespace = EMFExporterConstants.EMF_EXPORTER_NAMESPACE, name = EMFODSExporterConstants.EMF_EXPORTER_NAME)
public class EMFODSExporter extends AbstractEMFExporter implements EMFExporter {
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

	private static final int ID_COLUMN_WIDTH = 23;

	public EMFODSExporter() {
		super(LOG, Stopwatch.createStarted());
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

				LOG.info("Starting export of {} EObject(s) to ODS format"
						+ (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				if (!exportOptions.isEmpty()) {
					LOG.info("  Locale to use: {}", locale(exportOptions));
					LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
					LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
					LOG.info("  Adjust column width: {}", adjustColumnWidthEnabled(exportOptions));
					LOG.info("  Generate links for references: {}", generateLinksEnabled(exportOptions));
					LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
					LOG.info("  Show URIs instead of IDs (where applicable): {}", showURIsEnabled(exportOptions));
					LOG.info("  Show columns containing references: {}", showREFsEnabled(exportOptions));
				}

				ProcessedEObjectsDTO processedEObjectsDTO = exportEObjectsToMatrices(eObjects, exportOptions);

				exportMatricesToODS(outputStream, processedEObjectsDTO, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToODS(OutputStream outputStream, ProcessedEObjectsDTO processedEObjectsDTO,
			Map<Object, Object> exportOptions) throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of ODS sheets");

		Map<String, Table<Integer, Integer, Object>> matrixNameToEObjectMatrixMap = eObjectMatricesOnly(
				processedEObjectsDTO.matrixNameToMatrixMap);

		SpreadSheet document = new SpreadSheet();

		exportMatricesToODS(matrixNameToEObjectMatrixMap, exportOptions, document);

		if (exportMetadataEnabled(exportOptions)) {
			Map<String, Table<Integer, Integer, Object>> matrixNameToMetadataMatrixMap = metadataMatricesOnly(
					processedEObjectsDTO.matrixNameToMatrixMap);

			exportMatricesToODS(matrixNameToMetadataMatrixMap, exportOptions, document);
		}

		if (addMappingTableEnabled(exportOptions)) {
			Map<String, Table<Integer, Integer, Object>> matrixNameToMappingMatrixMap = mappingMatricesOnly(
					processedEObjectsDTO.matrixNameToMatrixMap);

			exportMatricesToODS(matrixNameToMappingMatrixMap, exportOptions, document);
		}

		document.save(outputStream);

		LOG.info("Finished generation of ODS sheets in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToODS(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Map<Object, Object> exportOptions, SpreadSheet document) throws IOException {
		for (String matrixName : matrixNameToMatrixMap.keySet()) {
			LOG.debug("Generating ODS sheet for matrix named '{}'", matrixName);

			Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

			exportMatrixToODS(document, matrixName, matrix, exportOptions);
		}
	}

	private void exportMatrixToODS(SpreadSheet document, String matrixName, Table<Integer, Integer, Object> matrix,
			Map<Object, Object> exportOptions) throws IOException {

		Sheet sheet = getOrConstructODSSheetIfNotExists(document, matrixName);

		constructODSSheetColumnHeaders(matrix, sheet, exportOptions);

		populateODSSheetWithData(document, matrix, sheet, exportOptions);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnsWidth(sheet);
		}
	}

	private Sheet getOrConstructODSSheetIfNotExists(SpreadSheet document, String matrixName) {
		Sheet sheet = document.getSheet(matrixName);
		if (sheet != null) {
			return sheet;
		}

		sheet = new Sheet(matrixName);
		document.appendSheet(sheet);
		return sheet;
	}

	private void constructODSSheetColumnHeaders(Table<Integer, Integer, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions) {

		Map<Integer, Object> matrixHeaderRow = matrix.row(getMatrixRowKey(1));

		// @formatter:off
		List<String> sheetColumnHeaders = matrixHeaderRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		int sheetColumnsCount = sheetColumnHeaders.size();

		sheet.appendColumns(sheetColumnsCount - sheet.getMaxColumns());

		Range sheetHeaderRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

		sheetHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < sheetColumnsCount; colIndex++) {
			constructODSSheetColumnHeaderCell(sheetHeaderRow, colIndex, sheetColumnHeaders.get(colIndex));
		}
	}

	private void populateODSSheetWithData(SpreadSheet document, Table<Integer, Integer, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions) {
		Map<Integer, Map<Integer, Object>> matrixRowMap = matrix.rowMap();

		// @formatter:off
		List<Integer> remainingRows = matrixRowMap.keySet()
				.stream()
				.skip(1)
				.collect(Collectors.toList());
		// @formatter:on

		for (Integer rowNumber : remainingRows) {
			Map<Integer, Object> row = matrixRowMap.get(rowNumber);

			int columnsCount = row.size();

			sheet.appendRow();

			Range sheetDataRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

			// @formatter:off
			List<Object> rowValues = row.values()
					.stream()
					.collect(Collectors.toList());
			// @formatter:on

			for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
				populateODSSheetCellWithData(document, sheetDataRow, colIndex, rowValues.get(colIndex), exportOptions);
			}
		}
	}

	private void populateODSSheetCellWithData(SpreadSheet document, Range sheetDataRow, int colIndex, Object value,
			Map<Object, Object> exportOptions) {

		if ((value != null) && !(value instanceof Optional) && String.valueOf(value) != null) {

			if (value instanceof EMFExportInternalIDValueCell) {
				setInternalIDValueCell(sheetDataRow, colIndex, (EMFExportInternalIDValueCell) value);

			} else if (value instanceof EMFExportEObjectIDValueCell) {
				setIDValueCell(sheetDataRow, colIndex, (EMFExportEObjectIDValueCell) value);

			} else if (value instanceof EMFExportEObjectOneReferenceValueCell) {
				setOneReferenceValueCell(document, sheetDataRow, colIndex,
						(EMFExportEObjectOneReferenceValueCell) value, exportOptions);

			} else if (value instanceof EMFExportEObjectManyReferencesValueCell) {
				setManyReferencesValueCell(document, sheetDataRow, colIndex,
						(EMFExportEObjectManyReferencesValueCell) value, exportOptions);

			} else if (value instanceof EMFExportMappingMatrixReferenceValueCell) {
				setMappingMatrixReferenceValueCell(document, sheetDataRow, colIndex,
						(EMFExportMappingMatrixReferenceValueCell) value, exportOptions);

			} else if (value instanceof Date) {
				setDateValueCell(sheetDataRow, colIndex, (Date) value);

			} else if (value instanceof Number) {
				setNumberValueCell(sheetDataRow, colIndex, (Number) value);

			} else if (value instanceof Boolean) {
				setBooleanValueCell(sheetDataRow, colIndex, (Boolean) value);

			} else {
				setStringValueCell(sheetDataRow, colIndex, String.valueOf(value));
			}

		} else {
			setVoidValueCell(sheetDataRow, colIndex);
		}
	}

	private void setInternalIDValueCell(Range dataRow, int colIndex, EMFExportInternalIDValueCell idValue) {
		dataRow.getCell(0, colIndex).setValue(idValue.hasValue() ? idValue.getValue() : "");
	}

	private void setIDValueCell(Range dataRow, int colIndex, EMFExportEObjectIDValueCell idValue) {
		dataRow.getCell(0, colIndex).setValue(idValue.hasValue() ? idValue.getValue() : "");
	}

	private void setOneReferenceValueCell(SpreadSheet document, Range dataRow, int colIndex,
			EMFExportEObjectOneReferenceValueCell referenceValueCell, Map<Object, Object> exportOptions) {

		String refValue = (showURIsEnabled(exportOptions) && !referenceValueCell.isSelfReferencingModel()
				&& referenceValueCell.hasURI()) ? referenceValueCell.getURI()
						: referenceValueCell.hasRefID() ? referenceValueCell.getRefID() : "";

		if (generateLinks(exportOptions, referenceValueCell.hasRefID())) {
			Sheet sheet = getOrConstructODSSheetIfNotExists(document, referenceValueCell.getRefMatrixName());

			LinkedValue linkedValue = LinkedValue.builder().value(refValue).href(sheet).build();
			dataRow.getCell(0, colIndex).addLinkedValue(linkedValue);
		} else {
			dataRow.getCell(0, colIndex).setValue(refValue);
		}
	}

	private void setManyReferencesValueCell(SpreadSheet document, Range dataRow, int colIndex,
			EMFExportEObjectManyReferencesValueCell referencesValueCell, Map<Object, Object> exportOptions) {

		if (generateLinks(exportOptions, referencesValueCell.hasRefIDs())) {
			List<LinkedValue> linkedValues = new ArrayList<LinkedValue>();

			Sheet sheet = getOrConstructODSSheetIfNotExists(document, referencesValueCell.getRefMatrixName());

			List<String> refValues = (showURIsEnabled(exportOptions) && !referencesValueCell.isSelfReferencingModel()
					&& referencesValueCell.hasURIs()) ? referencesValueCell.getURIs()
							: referencesValueCell.hasRefIDs() ? referencesValueCell.getRefIDs()
									: Collections.emptyList();

			for (String refValue : refValues) {
				LinkedValue linkedValue = LinkedValue.builder().value(refValue).href(sheet).build();
				linkedValues.add(linkedValue);
			}

			dataRow.getCell(0, colIndex).setLinkedValues(linkedValues);

		} else {
			dataRow.getCell(0, colIndex)
					.setValue(convertManyReferenceValuesToString(referencesValueCell, exportOptions));
		}
	}

	private boolean generateLinks(Map<Object, Object> exportOptions, boolean refHasValue) {
		return refHasValue && generateLinksEnabled(exportOptions) && exportNonContainmentEnabled(exportOptions);
	}

	private String convertManyReferenceValuesToString(EMFExportEObjectManyReferencesValueCell referencesValueCell,
			Map<Object, Object> exportOptions) {
		StringBuilder sb = new StringBuilder();

		List<String> manyReferencesValueCellValues = (showURIsEnabled(exportOptions)
				&& !referencesValueCell.isSelfReferencingModel() && referencesValueCell.hasURIs())
						? referencesValueCell.getURIs()
						: referencesValueCell.hasRefIDs() ? referencesValueCell.getRefIDs() : Collections.emptyList();

		if (!manyReferencesValueCellValues.isEmpty()) {
			Iterator<String> referenceValuesIt = manyReferencesValueCellValues.iterator();

			while (referenceValuesIt.hasNext()) {
				sb.append(referenceValuesIt.next());

				if (referenceValuesIt.hasNext()) {
					sb.append(System.lineSeparator());
				}
			}
		}

		return sb.toString();
	}

	private void setMappingMatrixReferenceValueCell(SpreadSheet document, Range dataRow, int colIndex,
			EMFExportMappingMatrixReferenceValueCell referenceValue, Map<Object, Object> exportOptions) {

		if (generateLinksEnabled(exportOptions)) {
			Sheet sheet = getOrConstructODSSheetIfNotExists(document, referenceValue.getMatrixName());

			LinkedValue linkedValue = LinkedValue.builder().value(referenceValue.getLabel()).href(sheet).build();
			dataRow.getCell(0, colIndex).addLinkedValue(linkedValue);
		} else {
			dataRow.getCell(0, colIndex).setValue(referenceValue.getLabel());
		}
	}

	private void constructODSSheetColumnHeaderCell(Range sheetHeaderRow, int colIndex, String sheetHeaderName) {
		sheetHeaderRow.getCell(0, colIndex).setValue(sheetHeaderName);
	}

	private void setStringValueCell(Range sheetDataRow, int colIndex, String value) {
		sheetDataRow.getCell(0, colIndex).setValue(value);

		if ((value != null) && value.length() > MAX_CHAR_PER_LINE_DEFAULT) {
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

	private void setVoidValueCell(Range sheetDataRow, int colIndex) {
		sheetDataRow.getCell(0, colIndex).clear();
	}

	private void adjustRowHeight(Sheet sheet, int rowIndex, String value) {
		sheet.setRowHeight(rowIndex, calculateRowHeight(value));
	}

	private Double calculateRowHeight(String value) {
		return (Double.valueOf(value.length() / MAX_CHAR_PER_LINE_DEFAULT) * 5);
	}

	private void adjustColumnsWidth(Sheet sheet) {
		int columnsCount = sheet.getMaxColumns();

		int rowsCount = sheet.getMaxRows();

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {

			int colMaxCharsCount = 0;

			for (int rowIndex = 0; rowIndex < rowsCount; rowIndex++) {

				if ((sheet.getRange(rowIndex, colIndex).getLinkedValues() != null)
						&& (sheet.getRange(rowIndex, colIndex).getLinkedValues().length > 0)
						&& (sheet.getRange(rowIndex, colIndex).getLinkedValues()[0].length > 0)
						&& !sheet.getRange(rowIndex, colIndex).getLinkedValues()[0][0].isEmpty()) {

					colMaxCharsCount = Math.max(colMaxCharsCount, ID_COLUMN_WIDTH);

				} else {

					Object cellValue = sheet.getRange(rowIndex, colIndex).getValue();

					if (cellValue != null && String.valueOf(cellValue) != null) {
						colMaxCharsCount = Math.max(colMaxCharsCount, String.valueOf(cellValue).length());
					}
				}
			}

			sheet.setColumnWidth(colIndex, calculateColumnWidth(colMaxCharsCount));
		}
	}

	private Double calculateColumnWidth(int charsCount) {
		return (Double.valueOf(charsCount) * 3);
	}

	@Override
	protected Map<Object, Object> validateExportOptions(Map<?, ?> options) throws EMFExportException {
		Map<Object, Object> exportOptions = super.validateExportOptions(options);

		if (!exportNonContainmentEnabled(exportOptions) && generateLinksEnabled(exportOptions)) {
			throw new EMFExportException(
					"Incompatible combination of export options: 'export non-containment references' option cannot be turned off if 'generate links for references' option is turned on!");
		}

		return exportOptions;
	}

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.TRUE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}
}
