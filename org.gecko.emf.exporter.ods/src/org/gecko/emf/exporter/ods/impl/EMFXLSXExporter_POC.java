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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.ods.api.EMFODSExportOptions;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
import com.github.miachm.sods.Color;
import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;
*/
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to XLSX format.
 * 
 * @author Michal H. Siemaszko
 */
/** This is a POC version of XLSX exporter, which is 
 *
 *	(A) more than 7 times faster than ODS exporter for same data set ('CityTree.xmi' with 3 distinct types of 42000+ records each) - 
 *		regardless if its {@link org.gecko.emf.exporter.ods.impl.EMFODSExporter} or 
 *		{@link org.gecko.emf.exporter.ods.impl.EMFODSExporter_REFACTORED_POC}, 
 *		since it's the underlying SODS library {@link https://github.com/miachm/SODS} which is the culprit, and 
 *
 *	(B) XLSX format can be opened in both LibreOffice/OpenOffice as well as Microsoft Office 
 *
 * .. - to be finalized **/
@Component(name = "EMFXLSXExporter_POC", scope = ServiceScope.PROTOTYPE)
public class EMFXLSXExporter_POC extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFXLSXExporter_POC.class);

//	private static final int MAX_CHAR_PER_LINE_DEFAULT = 30;
//
//	private static final int ID_COLUMN_WIDTH = 18;

	public EMFXLSXExporter_POC() {
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

				exportMatricesToXLSX(outputStream, mapOfMatrices, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToXLSX(OutputStream outputStream,
			ImmutableMap<String, Table<Integer, Character, Object>> mapOfMatrices, Map<Object, Object> exportOptions)
			throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of XLSX sheets");

		try (Workbook workbook = new XSSFWorkbook()) {
			for (String matrixName : mapOfMatrices.keySet()) {
				LOG.debug("Generating ODS sheet for matrix named '{}'", matrixName);

				Table<Integer, Character, Object> matrix = mapOfMatrices.get(matrixName);

				exportMatrixToXLSX(workbook, matrixName, matrix, exportOptions);
			}

			workbook.write(outputStream);
		}

		LOG.info("Finished generation of XLSX sheets in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatrixToXLSX(Workbook workbook, String matrixName, Table<Integer, Character, Object> matrix,
			Map<Object, Object> exportOptions) throws IOException {

		Sheet sheet = constructXLSXSheet(workbook, matrixName);

		constructXLSXSheetColumnHeaders(matrix, sheet, exportOptions);

		populateXLSXSheetWithData(matrix, sheet, exportOptions);
	}

	private Sheet constructXLSXSheet(Workbook workbook, String matrixName) {
		String safeSheetName = WorkbookUtil.createSafeSheetName(matrixName); // TODO: store safe names if needed for
																				// linking across sheets

		Sheet sheet = workbook.createSheet(safeSheetName);

		return sheet;
	}

	private void constructXLSXSheetColumnHeaders(Table<Integer, Character, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions) {
		Map<Character, Object> firstRow = matrix.row(Integer.valueOf(1));

		// @formatter:off
		List<String> odsSheetColumnHeaders = firstRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		int columnsCount = odsSheetColumnHeaders.size();

		Row headerRow = sheet.createRow(0);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			constructXLSXSheetColumnHeaderCell(headerRow, odsSheetColumnHeaders.get(colIndex), exportOptions, colIndex);
		}
	}

	private void populateXLSXSheetWithData(Table<Integer, Character, Object> matrix, Sheet sheet,
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

			int lastRowIndex = sheet.getLastRowNum();

			Row dataRow = sheet.createRow((lastRowIndex + 1));

			// @formatter:off
			List<Object> rowValues = row.values()
					.stream()
					.collect(Collectors.toList());
			// @formatter:on

			for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
				createXLSXSheetDataCell(dataRow, colIndex, rowValues.get(colIndex), exportOptions);
			}
		}
	}

	private void createXLSXSheetDataCell(Row dataRow, int colIndex, Object value, Map<Object, Object> exportOptions) {

		if ((value != null) && !(value instanceof Optional)) {

			if (value instanceof Date) {
				setDateValueCell(dataRow, colIndex, (Date) value);

			} else if (value instanceof Number) {
				setNumberValueCell(dataRow, colIndex, (Number) value);

			} else if (value instanceof Boolean) {
				setBooleanValueCell(dataRow, colIndex, (Boolean) value);

			} else if (value instanceof byte[]) {
				// TODO: clarify how byte arrays should be handled
				setStringValueCell(dataRow, colIndex, "EAttribute: byte[]");

			} else {
				setStringValueCell(dataRow, colIndex, String.valueOf(value));
			}

		} else {
			setVoidValueCell(dataRow, colIndex);
		}
	}

	private void constructXLSXSheetColumnHeaderCell(Row headerRow, String sheetHeaderName,
			Map<Object, Object> exportOptions, int colIndex) {

		constructXLSXSheetColumnHeaderCell(headerRow, colIndex, sheetHeaderName);

		if (adjustColumnWidthEnabled(exportOptions)) {
			// TODO
//			adjustColumnWidth(headerRow.getSheet(), colIndex, adjustColumnWidthCharsCount(sheetHeaderName));
		}
	}

	private void constructXLSXSheetColumnHeaderCell(Row headerRow, int colIndex, String sheetHeaderName) {
		Cell headerRowCell = headerRow.createCell(colIndex);
		headerRowCell.setCellValue(sheetHeaderName);
	}

	private void setStringValueCell(Row dataRow, int colIndex, String value) {
		Cell cell = dataRow.createCell(colIndex); // TODO: create cell in calling method and pass cell instead of row ?
		cell.setCellValue(value);
		
		// TODO
//		if (value.length() > MAX_CHAR_PER_LINE_DEFAULT) {
//			sheetDataRow.getCell(0, colIndex).setStyle(WRAPPED_DATA_CELL_STYLE);
//
//			adjustRowHeight(sheetDataRow.getSheet(), sheetDataRow.getRow(), value);
//		}
	}

	private void setDateValueCell(Row dataRow, int colIndex, Date value) {
		Cell cell = dataRow.createCell(colIndex); // TODO: create cell in calling method and pass cell instead of row ?
		cell.setCellValue(value);
	}

	private void setNumberValueCell(Row dataRow, int colIndex, Number value) {
		Cell cell = dataRow.createCell(colIndex); // TODO: create cell in calling method and pass cell instead of row ?
		cell.setCellValue(value.floatValue());
	}

	private void setBooleanValueCell(Row dataRow, int colIndex, Boolean value) {
		Cell cell = dataRow.createCell(colIndex); // TODO: create cell in calling method and pass cell instead of row ?
		cell.setCellValue(value);
	}

	private void setVoidValueCell(Row dataRow, int colIndex) {
		Cell cell = dataRow.createCell(colIndex); // TODO: create cell in calling method and pass cell instead of row ?
		cell.setBlank();
	}

//	private void adjustColumnWidth(Sheet sheet, int colIndex, int charsCount) {
//		if (sheet.getColumnWidth(colIndex) == null) {
//			sheet.setColumnWidth(colIndex, calculateColumnWidth(charsCount));
//		}
//	}	

//	private int adjustColumnWidthCharsCount(String sheetHeaderName) {
//		if (sheetHeaderName.equalsIgnoreCase(ID_COLUMN_NAME) || sheetHeaderName.endsWith(ID_COLUMN_NAME)
//				|| sheetHeaderName.startsWith(REF_COLUMN_PREFIX)) {
//			return ID_COLUMN_WIDTH;
//		} else {
//			return sheetHeaderName.length();
//		}
//	}
	
//	private Double calculateColumnWidth(int charsCount) {
//		return (Double.valueOf(charsCount) * (charsCount > 3 ? 5 : 7.5));
//	}

//	private void adjustRowHeight(Sheet sheet, int rowIndex, String value) {
//		sheet.setRowHeight(rowIndex, calculateRowHeight(value));
//	}

//	private Double calculateRowHeight(String value) {
//		return (Double.valueOf(value.length() / MAX_CHAR_PER_LINE_DEFAULT) * 5);
//	}	

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.FALSE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFODSExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}
}
