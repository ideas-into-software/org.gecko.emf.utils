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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.ods.api.EMFODSExportOptions;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.miachm.sods.Color;
import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to ODS format.
 * 
 * @author Michal H. Siemaszko
 */
/** This is a post-refactor version (i.e. after moving common logic to {@link org.gecko.emf.exporter.AbstractEMFExporter}) - to be finalized **/
@Component(name = "EMFODSExporter_REFACTORED_POC", scope = ServiceScope.PROTOTYPE)
//@Component(name = "EMFODSExporter", scope = ServiceScope.PROTOTYPE)
public class EMFODSExporter_REFACTORED_POC extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFODSExporter_REFACTORED_POC.class);

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

	private static final int ID_COLUMN_WIDTH = 18;

	public EMFODSExporter_REFACTORED_POC() {
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
				}

				ImmutableMap<String, Table<Integer, Character, Object>> mapOfMatrices = exportEObjectsToMatrices(
						eObjects, exportOptions);

				exportMatricesToODS(outputStream, mapOfMatrices, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToODS(OutputStream outputStream,
			ImmutableMap<String, Table<Integer, Character, Object>> mapOfMatrices, Map<Object, Object> exportOptions)
			throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of ODS sheets");

		SpreadSheet document = new SpreadSheet();

		for (String matrixName : mapOfMatrices.keySet()) {
			LOG.debug("Generating ODS sheet for matrix named '{}'", matrixName);

			Table<Integer, Character, Object> matrix = mapOfMatrices.get(matrixName);

			exportMatrixToODS(document, matrixName, matrix, exportOptions);
		}

		document.save(outputStream);

		LOG.info("Finished generation of ODS sheets in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatrixToODS(SpreadSheet document, String matrixName, Table<Integer, Character, Object> matrix,
			Map<Object, Object> exportOptions) throws IOException {

		Sheet sheet = constructODSSheet(document, matrixName);

		constructODSSheetColumnHeaders(matrix, sheet, exportOptions);

		populateODSSheetWithData(matrix, sheet, exportOptions);
	}

	private Sheet constructODSSheet(SpreadSheet document, String matrixName) {
		Sheet sheet = new Sheet(matrixName);
		document.appendSheet(sheet);
		return sheet;
	}

	private void constructODSSheetColumnHeaders(Table<Integer, Character, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions) {
		Map<Character, Object> firstRow = matrix.row(Integer.valueOf(1));

		// @formatter:off
		List<String> odsSheetColumnHeaders = firstRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		int columnsCount = odsSheetColumnHeaders.size();

		sheet.appendColumns((columnsCount - 1));

		Range odsSheetColumnHeaderRow = sheet.getRange(0, 0, 1, sheet.getMaxColumns());

		odsSheetColumnHeaderRow.setStyle(HEADER_STYLE);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			constructODSSheetColumnHeaderCell(odsSheetColumnHeaderRow, odsSheetColumnHeaders.get(colIndex),
					exportOptions, colIndex);
		}
	}

	private void populateODSSheetWithData(Table<Integer, Character, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions) {
		Map<Integer, Map<Character, Object>> matrixRowMap = matrix.rowMap();

		// @formatter:off
		List<Integer> remainingRows = matrixRowMap.keySet()
				.stream()
				.skip(1)
				.collect(Collectors.toList());
		// @formatter:on

		for (Integer rowNumber : remainingRows) {
			Map<Character, Object> row = matrixRowMap.get(rowNumber);

			int columnsCount = row.size();

			sheet.appendRow();

			Range odsSheetDataRow = sheet.getRange((sheet.getMaxRows() - 1), 0, 1, sheet.getMaxColumns());

			// @formatter:off
			List<Object> rowValues = row.values()
					.stream()
					.collect(Collectors.toList());
			// @formatter:on

			for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
				populateODSSheetCellWithData(odsSheetDataRow, colIndex, rowValues.get(colIndex), exportOptions);
			}
		}
	}

	private void populateODSSheetCellWithData(Range sheetDataRow, int colIndex, Object value,
			Map<Object, Object> exportOptions) {

		if ((value != null) && !(value instanceof Optional)) {

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
				setStringValueCell(sheetDataRow, colIndex, String.valueOf(value));
			}

		} else {
			setVoidValueCell(sheetDataRow, colIndex);
		}
	}

	private void constructODSSheetColumnHeaderCell(Range sheetHeaderRow, String sheetHeaderName,
			Map<Object, Object> exportOptions, int colIndex) {

		constructODSSheetColumnHeaderCell(sheetHeaderRow, colIndex, sheetHeaderName);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(sheetHeaderRow.getSheet(), colIndex, adjustColumnWidthCharsCount(sheetHeaderName));
		}
	}

	private void constructODSSheetColumnHeaderCell(Range sheetHeaderRow, int colIndex, String sheetHeaderName) {
		sheetHeaderRow.getCell(0, colIndex).setValue(sheetHeaderName);
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

	private void setVoidValueCell(Range sheetDataRow, int colIndex) {
		sheetDataRow.getCell(0, colIndex).clear();
	}

	private int adjustColumnWidthCharsCount(String sheetHeaderName) {
		if (sheetHeaderName.equalsIgnoreCase(ID_COLUMN_NAME) || sheetHeaderName.endsWith(ID_COLUMN_NAME)
				|| sheetHeaderName.startsWith(REF_COLUMN_PREFIX)) {
			return ID_COLUMN_WIDTH;
		} else {
			return sheetHeaderName.length();
		}
	}

	private void adjustRowHeight(Sheet sheet, int rowIndex, String value) {
		sheet.setRowHeight(rowIndex, calculateRowHeight(value));
	}

	private Double calculateRowHeight(String value) {
		return (Double.valueOf(value.length() / MAX_CHAR_PER_LINE_DEFAULT) * 5);
	}

	private void adjustColumnWidth(Sheet sheet, int colIndex, int charsCount) {
		if (sheet.getColumnWidth(colIndex) == null) {
			sheet.setColumnWidth(colIndex, calculateColumnWidth(charsCount));
		}
	}

	private Double calculateColumnWidth(int charsCount) {
		return (Double.valueOf(charsCount) * (charsCount > 3 ? 5 : 7.5));
	}

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.FALSE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}
}
