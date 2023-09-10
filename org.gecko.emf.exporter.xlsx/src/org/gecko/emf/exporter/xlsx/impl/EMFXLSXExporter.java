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
package org.gecko.emf.exporter.xlsx.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportEObjectIDValueCell;
import org.gecko.emf.exporter.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExportMappingMatrixReferenceValueCell;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.xlsx.api.EMFXLSXExportOptions;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to XLSX format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFXLSXExporter", scope = ServiceScope.PROTOTYPE)
public class EMFXLSXExporter extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFXLSXExporter.class);

	public EMFXLSXExporter() {
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

				LOG.info("Starting export of {} EObject(s) to XLSX format"
						+ (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				if (!exportOptions.isEmpty()) {
					LOG.info("  Locale to use: {}", locale(exportOptions));
					LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
					LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
					LOG.info("  Adjust column width: {}", adjustColumnWidthEnabled(exportOptions));
					LOG.info("  Generate links for references: {}", generateLinksEnabled(exportOptions));
					LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
				}

				Map<String, Table<Integer, Character, Object>> mapOfMatrices = exportEObjectsToMatrices(eObjects,
						exportOptions);

				exportMatricesToXLSX(outputStream, mapOfMatrices, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToXLSX(OutputStream outputStream,
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, Map<Object, Object> exportOptions)
			throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of XLSX sheets");

		Map<String, Table<Integer, Character, Object>> mapOfEObjectMatrices = eObjectMatricesOnly(mapOfMatrices);

		try (Workbook workbook = new XSSFWorkbook()) {

			XSSFFont headerCellFont = createHeaderCellFont(workbook);

			CellStyle headerCellStyle = createHeaderCellStyle(workbook, headerCellFont);

			XSSFFont dataCellFont = createDataCellFont(workbook);

			CellStyle genericDataCellStyle = createGenericDataCellStyle(workbook, dataCellFont);

			CellStyle dateDataCellStyle = createDateDataCellStyle(workbook, dataCellFont);

			CreationHelper creationHelper = workbook.getCreationHelper();

			exportMatricesToXLSX(exportOptions, mapOfEObjectMatrices, workbook, creationHelper, headerCellStyle,
					genericDataCellStyle, dateDataCellStyle);

			if (exportMetadataEnabled(exportOptions)) {
				Map<String, Table<Integer, Character, Object>> mapOfMetadataMatrices = metadataMatricesOnly(
						mapOfMatrices);

				exportMatricesToXLSX(exportOptions, mapOfMetadataMatrices, workbook, creationHelper, headerCellStyle,
						genericDataCellStyle, dateDataCellStyle);
			}

			if (addMappingTableEnabled(exportOptions)) {
				Map<String, Table<Integer, Character, Object>> mapOfMappingMatrices = mappingMatricesOnly(
						mapOfMatrices);

				exportMatricesToXLSX(exportOptions, mapOfMappingMatrices, workbook, creationHelper, headerCellStyle,
						genericDataCellStyle, dateDataCellStyle);
			}

			workbook.write(outputStream);
		}

		LOG.info("Finished generation of XLSX sheets in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToXLSX(Map<Object, Object> exportOptions,
			Map<String, Table<Integer, Character, Object>> mapOfEObjectMatrices, Workbook workbook,
			CreationHelper creationHelper, CellStyle headerCellStyle, CellStyle genericDataCellStyle,
			CellStyle dateDataCellStyle) throws IOException {
		for (String matrixName : mapOfEObjectMatrices.keySet()) {
			LOG.debug("Generating XLSX sheet for matrix named '{}'", matrixName);

			Table<Integer, Character, Object> matrix = mapOfEObjectMatrices.get(matrixName);

			exportMatrixToXLSX(workbook, matrixName, matrix, exportOptions, creationHelper, headerCellStyle,
					genericDataCellStyle, dateDataCellStyle);
		}
	}

	private void exportMatrixToXLSX(Workbook workbook, String matrixName, Table<Integer, Character, Object> matrix,
			Map<Object, Object> exportOptions, CreationHelper creationHelper, CellStyle headerCellStyle,
			CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) throws IOException {

		Sheet sheet = constructXLSXSheet(workbook, matrixName);

		boolean hasTypeLevelMetadataDocumentation = hasTypeLevelMetadataDocumentation(sheet,
				matrix.row(Integer.valueOf(1)));
		if (hasTypeLevelMetadataDocumentation) {
			LOG.debug("Matrix named '{}' has type level metadata documentation ", matrixName);

			setTypeLevelMetadataDocumentation(matrix, sheet, headerCellStyle, genericDataCellStyle);
		}

		constructXLSXSheetColumnHeaders(matrix, sheet, hasTypeLevelMetadataDocumentation, exportOptions,
				headerCellStyle, genericDataCellStyle);

		populateXLSXSheetWithData(matrix, sheet, hasTypeLevelMetadataDocumentation, exportOptions, creationHelper,
				genericDataCellStyle, dateDataCellStyle);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(sheet, hasTypeLevelMetadataDocumentation);
		}

		if (freezeHeaderRowEnabled(exportOptions)) {
			freezeHeaderRow(sheet, hasTypeLevelMetadataDocumentation);
		}
	}

	private Sheet constructXLSXSheet(Workbook workbook, String matrixName) {
		return workbook.createSheet(matrixName);
	}

	private void constructXLSXSheetColumnHeaders(Table<Integer, Character, Object> matrix, Sheet sheet,
			boolean hasTypeLevelMetadataDocumentation, Map<Object, Object> exportOptions, CellStyle headerCellStyle,
			CellStyle genericDataCellStyle) {

		Map<Character, Object> matrixHeaderRow = matrix.row(Integer.valueOf(hasTypeLevelMetadataDocumentation ? 2 : 1));

		// @formatter:off
		List<String> sheetColumnHeaders = matrixHeaderRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		int sheetColumnsCount = sheetColumnHeaders.size();

		Row sheetHeaderRow = sheet.createRow(hasTypeLevelMetadataDocumentation ? 1 : 0);

		for (int colIndex = 0; colIndex < sheetColumnsCount; colIndex++) {
			constructXLSXSheetColumnHeaderCell(sheetHeaderRow, sheetColumnHeaders.get(colIndex), headerCellStyle,
					colIndex);
		}
	}

	private void populateXLSXSheetWithData(Table<Integer, Character, Object> matrix, Sheet sheet,
			boolean hasTypeLevelMetadataDocumentation, Map<Object, Object> exportOptions, CreationHelper creationHelper,
			CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) {
		Map<Integer, Map<Character, Object>> matrixRowMap = matrix.rowMap();

		// @formatter:off
		List<Integer> remainingRows = matrixRowMap.keySet()
				.stream()
				.skip(hasTypeLevelMetadataDocumentation ? 2 : 1)
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
				createXLSXSheetDataCell(dataRow, colIndex, rowValues.get(colIndex), exportOptions, creationHelper,
						genericDataCellStyle, dateDataCellStyle);
			}
		}
	}

	private void createXLSXSheetDataCell(Row dataRow, int colIndex, Object value, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) {
		if ((value != null) && !(value instanceof Optional)) {

			if (value instanceof EMFExportEObjectIDValueCell) {
				setIDValueCell(dataRow, colIndex, (EMFExportEObjectIDValueCell) value, exportOptions, creationHelper,
						genericDataCellStyle);

			} else if (value instanceof EMFExportEObjectOneReferenceValueCell) {
				setOneReferenceValueCell(dataRow, colIndex, (EMFExportEObjectOneReferenceValueCell) value,
						exportOptions, creationHelper, genericDataCellStyle);

			} else if (value instanceof EMFExportEObjectManyReferencesValueCell) {
				setManyReferencesValueCell(dataRow, colIndex, (EMFExportEObjectManyReferencesValueCell) value,
						exportOptions, creationHelper, genericDataCellStyle);

			} else if (value instanceof EMFExportMappingMatrixReferenceValueCell) {
				setMappingMatrixReferenceValueCell(dataRow, colIndex, (EMFExportMappingMatrixReferenceValueCell) value,
						exportOptions, creationHelper, genericDataCellStyle);

			} else if (value instanceof Date) {
				setDateValueCell(dataRow, colIndex, (Date) value, dateDataCellStyle);

			} else if (value instanceof Number) {
				setNumberValueCell(dataRow, colIndex, (Number) value, genericDataCellStyle);

			} else if (value instanceof Boolean) {
				setBooleanValueCell(dataRow, colIndex, (Boolean) value, genericDataCellStyle);

			} else if (value instanceof byte[]) {
				// TODO: clarify how byte arrays should be handled
				setStringValueCell(dataRow, colIndex, "EAttribute: byte[]", genericDataCellStyle);

			} else {
				setStringValueCell(dataRow, colIndex, String.valueOf(value), genericDataCellStyle);
			}

		} else {
			setVoidValueCell(dataRow, colIndex);
		}
	}

	private void setIDValueCell(Row dataRow, int colIndex, EMFExportEObjectIDValueCell idValue,
			Map<Object, Object> exportOptions, CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(idValue.getValue());
		cell.setCellStyle(genericDataCellStyle);
	}

	private void setOneReferenceValueCell(Row dataRow, int colIndex,
			EMFExportEObjectOneReferenceValueCell referenceValue, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(referenceValue.getValue());
		cell.setCellStyle(genericDataCellStyle);

		if (generateLinksEnabled(exportOptions)) {
			if (eObjectIDToMatrixNameMap.containsKey(referenceValue.getValue())) {
				String matrixName = eObjectIDToMatrixNameMap.get(referenceValue.getValue());

				Hyperlink link = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
				link.setAddress(constructHyperlinkAddress(matrixName));
				cell.setHyperlink(link);
			}
		}
	}

	private void setManyReferencesValueCell(Row dataRow, int colIndex,
			EMFExportEObjectManyReferencesValueCell referenceValues, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(convertManyReferenceValuesToString(referenceValues));
		cell.setCellStyle(genericDataCellStyle);
		if (referenceValues.getValuesCount() > 1) {
			cell.getRow().setHeightInPoints(
					cell.getSheet().getDefaultRowHeightInPoints() * referenceValues.getValues().size());
		}

		// multiple-line text, as in case of many reference values, is incompatible with links - 
		//	once hyperlink is set, multi-line text becomes one long string;
		// neither is multiple hyperlinks per cell supported 
		/*
		if (generateLinksEnabled(exportOptions)) {
			String firstValue = referenceValues.getValues().get(0);
			if (eObjectIDToMatrixNameMap.containsKey(firstValue)) {
				String matrixName = eObjectIDToMatrixNameMap.get(firstValue);
				
				Hyperlink link = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
				link.setAddress(constructHyperlinkAddress(matrixName));
				cell.setHyperlink(link);
			}
		}
		*/		
	}

	private String convertManyReferenceValuesToString(EMFExportEObjectManyReferencesValueCell referenceValues) {
		StringBuilder sb = new StringBuilder();

		Iterator<String> referenceValuesIt = referenceValues.getValues().iterator();

		while (referenceValuesIt.hasNext()) {
			sb.append(referenceValuesIt.next());

			if (referenceValuesIt.hasNext()) {
				sb.append(System.lineSeparator());
			}
		}

		return sb.toString();
	}

	private void setMappingMatrixReferenceValueCell(Row dataRow, int colIndex,
			EMFExportMappingMatrixReferenceValueCell referenceValue, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(referenceValue.getLabel());
		cell.setCellStyle(genericDataCellStyle);

		if (generateLinksEnabled(exportOptions)) {
			Hyperlink link = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
			link.setAddress(constructHyperlinkAddress(referenceValue.getMatrixName()));
			cell.setHyperlink(link);
		}
	}

	private String constructHyperlinkAddress(String matrixName) {
		StringBuilder sb = new StringBuilder();

		sb.append("'");
		sb.append(matrixName);
		sb.append("'");
		sb.append("!A1");

		return sb.toString();
	}

	private void constructXLSXSheetColumnHeaderCell(Row headerRow, String sheetHeaderName, CellStyle headerCellStyle,
			int colIndex) {
		Cell headerRowCell = headerRow.createCell(colIndex);
		headerRowCell.setCellValue(sheetHeaderName);
		headerRowCell.setCellStyle(headerCellStyle);
	}

	private void setStringValueCell(Row dataRow, int colIndex, String value, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(value);
		cell.setCellStyle(genericDataCellStyle);

		if (value.contains(System.lineSeparator())) {
			int linesCount = value.split(System.lineSeparator()).length;
			if (linesCount > 1) {
				cell.getRow().setHeightInPoints(cell.getSheet().getDefaultRowHeightInPoints() * linesCount);
			}
		}
	}

	private void setDateValueCell(Row dataRow, int colIndex, Date value, CellStyle dateDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(value);
		cell.setCellStyle(dateDataCellStyle);
	}

	private void setNumberValueCell(Row dataRow, int colIndex, Number value, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(value.floatValue());
		cell.setCellStyle(genericDataCellStyle);
	}

	private void setBooleanValueCell(Row dataRow, int colIndex, Boolean value, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(value);
		cell.setCellStyle(genericDataCellStyle);
	}

	private void setVoidValueCell(Row dataRow, int colIndex) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setBlank();
	}

	private void adjustColumnWidth(Sheet sheet, boolean hasTypeLevelMetadataDocumentation) {
		Row firstRow = sheet.getRow(hasTypeLevelMetadataDocumentation ? 1 : 0);
		short minColIx = firstRow.getFirstCellNum();
		short maxColIx = firstRow.getLastCellNum();
		for (short colIndex = minColIx; colIndex < maxColIx; colIndex++) {
			Cell cell = firstRow.getCell(colIndex);
			if (cell == null) {
				continue;
			}
			sheet.autoSizeColumn(colIndex);
		}
	}

	private void freezeHeaderRow(Sheet sheet, boolean hasTypeLevelMetadataDocumentation) {
		sheet.createFreezePane(0, (hasTypeLevelMetadataDocumentation ? 2 : 1));
	}

	private boolean hasTypeLevelMetadataDocumentation(Sheet sheet, Map<Character, Object> firstRow) {
		boolean isMetadataSheet = (sheet.getSheetName() != null
				&& sheet.getSheetName().contains(METADATA_MATRIX_NAME_SUFFIX));
		boolean isTypeLevelMetadataDocumentationPresent = (firstRow.size() == 2)
				&& firstRow.containsKey(getMatrixColumnName(0))
				&& String.valueOf(firstRow.get(getMatrixColumnName(0))).equalsIgnoreCase(METADATA_DOCUMENTATION_HEADER);

		return isMetadataSheet && isTypeLevelMetadataDocumentationPresent;
	}

	private void setTypeLevelMetadataDocumentation(Table<Integer, Character, Object> matrix, Sheet sheet,
			CellStyle headerCellStyle, CellStyle genericDataCellStyle) {
		Row typeLevelMetadataDocumentationRow = sheet.createRow(0);

		constructTypeLevelMetadataDocumentationValueCell(typeLevelMetadataDocumentationRow,
				METADATA_DOCUMENTATION_HEADER, headerCellStyle, 0);

		constructTypeLevelMetadataDocumentationValueCell(typeLevelMetadataDocumentationRow,
				String.valueOf(matrix.get(Integer.valueOf(1), getMatrixColumnName(1))), genericDataCellStyle, 1);
	}

	private void constructTypeLevelMetadataDocumentationValueCell(Row row, String cellValue, CellStyle cellStyle,
			int colIndex) {
		Cell headerRowCell = row.createCell(colIndex);
		headerRowCell.setCellValue(cellValue);
		headerRowCell.setCellStyle(cellStyle);
	}

	private CellStyle createHeaderCellStyle(Workbook workbook, XSSFFont font) {
		CellStyle headerCellStyle = workbook.createCellStyle();
		headerCellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
		headerCellStyle.setFont(font);
		return headerCellStyle;
	}

	private XSSFFont createHeaderCellFont(Workbook workbook) {
		XSSFFont font = ((XSSFWorkbook) workbook).createFont();
		font.setFontName("Arial");
		font.setFontHeightInPoints((short) 10);
		font.setBold(true);
		font.setColor(IndexedColors.BLACK.getIndex());
		return font;
	}

	private CellStyle createGenericDataCellStyle(Workbook workbook, XSSFFont font) {
		CellStyle dataCellStyle = workbook.createCellStyle();
		dataCellStyle.setFont(font);
		dataCellStyle.setWrapText(true);
		return dataCellStyle;
	}

	private XSSFFont createDataCellFont(Workbook workbook) {
		XSSFFont font = ((XSSFWorkbook) workbook).createFont();
		font.setFontName("Arial");
		font.setFontHeightInPoints((short) 10);
		font.setBold(false);
		font.setColor(IndexedColors.BLACK.getIndex());
		return font;
	}

	private CellStyle createDateDataCellStyle(Workbook workbook, XSSFFont font) {
		CellStyle dataCellStyle = workbook.createCellStyle();
		dataCellStyle.setFont(font);
		dataCellStyle.setDataFormat((short) 22);
		return dataCellStyle;
	}

	private boolean adjustColumnWidthEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.FALSE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}

	private boolean freezeHeaderRowEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_FREEZE_HEADER_ROW, Boolean.FALSE));
	}
}
