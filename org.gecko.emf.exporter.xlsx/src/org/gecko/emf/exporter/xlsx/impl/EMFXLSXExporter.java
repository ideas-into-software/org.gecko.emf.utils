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
import java.util.Collections;
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
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.EMFExporterConstants;
import org.gecko.emf.exporter.cells.EMFExportEObjectIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.cells.EMFExportInternalIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportMappingMatrixReferenceValueCell;
import org.gecko.emf.exporter.xlsx.api.EMFXLSXExportOptions;
import org.gecko.emf.exporter.xlsx.api.EMFXLSXExporterConstants;
import org.osgi.annotation.bundle.Capability;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting
 * EMF resources and lists of EMF objects to XLSX format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = EMFXLSXExporterConstants.EMF_EXPORTER_NAME, scope = ServiceScope.PROTOTYPE)
@Capability(namespace = EMFExporterConstants.EMF_EXPORTER_NAMESPACE, name = EMFXLSXExporterConstants.EMF_EXPORTER_NAME)
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
					LOG.info("  Show URIs instead of IDs (where applicable): {}", showURIsEnabled(exportOptions));
					LOG.info("  Show columns containing references: {}", showREFsEnabled(exportOptions));
				}

				ProcessedEObjectsDTO processedEObjectsDTO = exportEObjectsToMatrices(eObjects, exportOptions);

				exportMatricesToXLSX(outputStream, processedEObjectsDTO, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToXLSX(OutputStream outputStream, ProcessedEObjectsDTO processedEObjectsDTO,
			Map<Object, Object> exportOptions) throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of XLSX sheets");

		Map<String, Table<Integer, Integer, Object>> matrixNameToEObjectMatrixMap = eObjectMatricesOnly(
				processedEObjectsDTO.matrixNameToMatrixMap);

		try (Workbook workbook = new XSSFWorkbook()) {

			XSSFFont headerCellFont = createHeaderCellFont(workbook);

			CellStyle headerCellStyle = createHeaderCellStyle(workbook, headerCellFont);

			XSSFFont dataCellFont = createDataCellFont(workbook);

			CellStyle genericDataCellStyle = createGenericDataCellStyle(workbook, dataCellFont);

			CellStyle dateDataCellStyle = createDateDataCellStyle(workbook, dataCellFont);

			CreationHelper creationHelper = workbook.getCreationHelper();

			exportMatricesToXLSX(processedEObjectsDTO, exportOptions, matrixNameToEObjectMatrixMap, workbook,
					creationHelper, headerCellStyle, genericDataCellStyle, dateDataCellStyle);

			if (exportMetadataEnabled(exportOptions)) {
				Map<String, Table<Integer, Integer, Object>> matrixNameToMetadataMatrixMap = metadataMatricesOnly(
						processedEObjectsDTO.matrixNameToMatrixMap);

				exportMatricesToXLSX(processedEObjectsDTO, exportOptions, matrixNameToMetadataMatrixMap, workbook,
						creationHelper, headerCellStyle, genericDataCellStyle, dateDataCellStyle);
			}

			if (addMappingTableEnabled(exportOptions)) {
				Map<String, Table<Integer, Integer, Object>> matrixNameToMappingMatrixMap = mappingMatricesOnly(
						processedEObjectsDTO.matrixNameToMatrixMap);

				exportMatricesToXLSX(processedEObjectsDTO, exportOptions, matrixNameToMappingMatrixMap, workbook,
						creationHelper, headerCellStyle, genericDataCellStyle, dateDataCellStyle);
			}

			workbook.write(outputStream);
		}

		LOG.info("Finished generation of XLSX sheets in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToXLSX(ProcessedEObjectsDTO processedEObjectsDTO, Map<Object, Object> exportOptions,
			Map<String, Table<Integer, Integer, Object>> matrixNameToEObjectMatrixMap, Workbook workbook,
			CreationHelper creationHelper, CellStyle headerCellStyle, CellStyle genericDataCellStyle,
			CellStyle dateDataCellStyle) throws IOException {
		for (String matrixName : matrixNameToEObjectMatrixMap.keySet()) {
			LOG.debug("Generating XLSX sheet for matrix named '{}'", matrixName);

			Table<Integer, Integer, Object> matrix = matrixNameToEObjectMatrixMap.get(matrixName);

			exportMatrixToXLSX(processedEObjectsDTO, workbook, matrixName, matrix, exportOptions, creationHelper,
					headerCellStyle, genericDataCellStyle, dateDataCellStyle);
		}
	}

	private void exportMatrixToXLSX(ProcessedEObjectsDTO processedEObjectsDTO, Workbook workbook, String matrixName,
			Table<Integer, Integer, Object> matrix, Map<Object, Object> exportOptions, CreationHelper creationHelper,
			CellStyle headerCellStyle, CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) throws IOException {

		Sheet sheet = constructXLSXSheet(workbook, matrixName);

		constructXLSXSheetColumnHeaders(matrix, sheet, exportOptions, headerCellStyle, genericDataCellStyle);

		populateXLSXSheetWithData(processedEObjectsDTO, matrix, sheet, exportOptions, creationHelper,
				genericDataCellStyle, dateDataCellStyle);

		if (adjustColumnWidthEnabled(exportOptions)) {
			adjustColumnWidth(sheet);
		}

		if (freezeHeaderRowEnabled(exportOptions)) {
			freezeHeaderRow(sheet);
		}
	}

	private Sheet constructXLSXSheet(Workbook workbook, String matrixName) {
		return workbook.createSheet(matrixName);
	}

	private void constructXLSXSheetColumnHeaders(Table<Integer, Integer, Object> matrix, Sheet sheet,
			Map<Object, Object> exportOptions, CellStyle headerCellStyle, CellStyle genericDataCellStyle) {

		Map<Integer, Object> matrixHeaderRow = matrix.row(getMatrixRowKey(1));

		// @formatter:off
		List<String> sheetColumnHeaders = matrixHeaderRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		int sheetColumnsCount = sheetColumnHeaders.size();

		Row sheetHeaderRow = sheet.createRow(0);

		for (int colIndex = 0; colIndex < sheetColumnsCount; colIndex++) {
			constructXLSXSheetColumnHeaderCell(sheetHeaderRow, sheetColumnHeaders.get(colIndex), headerCellStyle,
					colIndex);
		}
	}

	private void populateXLSXSheetWithData(ProcessedEObjectsDTO processedEObjectsDTO,
			Table<Integer, Integer, Object> matrix, Sheet sheet, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) {
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

			int lastRowIndex = sheet.getLastRowNum();

			Row dataRow = sheet.createRow((lastRowIndex + 1));

			// @formatter:off
			List<Object> rowValues = row.values()
					.stream()
					.collect(Collectors.toList());
			// @formatter:on

			for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
				populateXLSXSheetCellWithData(processedEObjectsDTO, dataRow, colIndex, rowValues.get(colIndex),
						exportOptions, creationHelper, genericDataCellStyle, dateDataCellStyle);
			}
		}
	}

	private void populateXLSXSheetCellWithData(ProcessedEObjectsDTO processedEObjectsDTO, Row dataRow, int colIndex,
			Object value, Map<Object, Object> exportOptions, CreationHelper creationHelper,
			CellStyle genericDataCellStyle, CellStyle dateDataCellStyle) {
		if ((value != null) && !(value instanceof Optional)) {

			if (value instanceof EMFExportInternalIDValueCell) {
				setInternalIDValueCell(dataRow, colIndex, (EMFExportInternalIDValueCell) value, exportOptions,
						creationHelper, genericDataCellStyle);

			} else if (value instanceof EMFExportEObjectIDValueCell) {
				setIDValueCell(dataRow, colIndex, (EMFExportEObjectIDValueCell) value, exportOptions, creationHelper,
						genericDataCellStyle);

			} else if (value instanceof EMFExportEObjectOneReferenceValueCell) {
				setOneReferenceValueCell(processedEObjectsDTO, dataRow, colIndex,
						(EMFExportEObjectOneReferenceValueCell) value, exportOptions, creationHelper,
						genericDataCellStyle);

			} else if (value instanceof EMFExportEObjectManyReferencesValueCell) {
				setManyReferencesValueCell(processedEObjectsDTO, dataRow, colIndex,
						(EMFExportEObjectManyReferencesValueCell) value, exportOptions, creationHelper,
						genericDataCellStyle);

			} else if (value instanceof EMFExportMappingMatrixReferenceValueCell) {
				setMappingMatrixReferenceValueCell(dataRow, colIndex, (EMFExportMappingMatrixReferenceValueCell) value,
						exportOptions, creationHelper, genericDataCellStyle);

			} else if (value instanceof Date) {
				setDateValueCell(dataRow, colIndex, (Date) value, dateDataCellStyle);

			} else if (value instanceof Number) {
				setNumberValueCell(dataRow, colIndex, (Number) value, genericDataCellStyle);

			} else if (value instanceof Boolean) {
				setBooleanValueCell(dataRow, colIndex, (Boolean) value, genericDataCellStyle);

			} else {
				setStringValueCell(dataRow, colIndex, String.valueOf(value), genericDataCellStyle);
			}

		} else {
			setVoidValueCell(dataRow, colIndex);
		}
	}

	private void setInternalIDValueCell(Row dataRow, int colIndex, EMFExportInternalIDValueCell internalIdValue,
			Map<Object, Object> exportOptions, CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(internalIdValue.hasValue() ? internalIdValue.getValue() : "");
		cell.setCellStyle(genericDataCellStyle);
	}

	private void setIDValueCell(Row dataRow, int colIndex, EMFExportEObjectIDValueCell idValue,
			Map<Object, Object> exportOptions, CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(idValue.hasValue() ? idValue.getValue() : "");
		cell.setCellStyle(genericDataCellStyle);
	}

	private void setOneReferenceValueCell(ProcessedEObjectsDTO processedEObjectsDTO, Row dataRow, int colIndex,
			EMFExportEObjectOneReferenceValueCell referenceValueCell, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);

		if (showURIsEnabled(exportOptions) && !referenceValueCell.isSelfReferencingModel()) {
			cell.setCellValue(referenceValueCell.hasURI() ? referenceValueCell.getURI() : "");
		} else {
			cell.setCellValue(referenceValueCell.hasRefID() ? referenceValueCell.getRefID() : "");
		}

		cell.setCellStyle(genericDataCellStyle);

		if (generateLinksEnabled(exportOptions) && referenceValueCell.hasRefID()) {
			if (processedEObjectsDTO.eObjectIDToMatrixNameMap.containsKey(referenceValueCell.getRefID())) {
				String matrixName = processedEObjectsDTO.eObjectIDToMatrixNameMap.get(referenceValueCell.getRefID());

				Hyperlink link = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
				link.setAddress(constructHyperlinkAddress(matrixName));
				cell.setHyperlink(link);
			}
		}
	}

	private void setManyReferencesValueCell(ProcessedEObjectsDTO processedEObjectsDTO, Row dataRow, int colIndex,
			EMFExportEObjectManyReferencesValueCell referencesValueCell, Map<Object, Object> exportOptions,
			CreationHelper creationHelper, CellStyle genericDataCellStyle) {
		Cell cell = dataRow.createCell(colIndex);
		cell.setCellValue(convertManyReferenceValuesToString(referencesValueCell, exportOptions));
		cell.setCellStyle(genericDataCellStyle);
		if (referencesValueCell.getRefIDsCount() > 1) {
			cell.getRow().setHeightInPoints(
					cell.getSheet().getDefaultRowHeightInPoints() * referencesValueCell.getRefIDs().size());
		}

		// multiple-line text, as in case of many reference values, is incompatible with
		// links -
		// once hyperlink is set, multi-line text becomes one long string;
		// neither is having multiple hyperlinks per cell supported
		// .. therefore, links are created only if there's at most one ref in list of
		// one-to-many refs
		if (generateLinksEnabled(exportOptions) && referencesValueCell.getRefIDsCount() == 1) {
			if (processedEObjectsDTO.eObjectIDToMatrixNameMap.containsKey(referencesValueCell.getRefIDs().get(0))) {
				String matrixName = processedEObjectsDTO.eObjectIDToMatrixNameMap
						.get(referencesValueCell.getRefIDs().get(0));

				Hyperlink link = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
				link.setAddress(constructHyperlinkAddress(matrixName));
				cell.setHyperlink(link);
			}
		}
	}

	private String convertManyReferenceValuesToString(EMFExportEObjectManyReferencesValueCell referenceValues,
			Map<Object, Object> exportOptions) {
		StringBuilder sb = new StringBuilder();

		List<String> manyReferencesValueCellValues = (showURIsEnabled(exportOptions)
				&& !referenceValues.isSelfReferencingModel() && referenceValues.hasURIs()) ? referenceValues.getURIs()
						: referenceValues.hasRefIDs() ? referenceValues.getRefIDs() : Collections.emptyList();

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

		if ((value != null) && value.contains(System.lineSeparator())) {
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

	private void adjustColumnWidth(Sheet sheet) {
		Row firstRow = sheet.getRow(0);
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

	private void freezeHeaderRow(Sheet sheet) {
		sheet.createFreezePane(0, 1);
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
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_ADJUST_COLUMN_WIDTH, Boolean.TRUE));
	}

	private boolean generateLinksEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_GENERATE_LINKS, Boolean.FALSE));
	}

	private boolean freezeHeaderRowEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFXLSXExportOptions.OPTION_FREEZE_HEADER_ROW, Boolean.TRUE));
	}
}
