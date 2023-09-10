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
package org.gecko.emf.exporter.csv.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.csv.api.EMFCSVExportMode;
import org.gecko.emf.exporter.csv.api.EMFCSVExportOptions;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Table;

import de.siegmar.fastcsv.writer.CsvWriter;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting EMF resources and lists of EMF objects to CSV format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFCSVExporter", scope = ServiceScope.PROTOTYPE)
public class EMFCSVExporter extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFCSVExporter.class);

	private static final String CSV_FILE_EXTENSION = "csv";
	
	public EMFCSVExporter() {
		super(LOG, Stopwatch.createStarted());
	}

	/* 
	 * Depending on export mode used (FLAT, ZIP), 'java.io.OutputStream' passed is either a file or a ZIP archive to which files are collected.
	 * 
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

				LOG.info("Starting export of {} EObject(s) to CSV format"
						+ (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				if (!exportOptions.isEmpty()) {
					LOG.info("  Export mode: " + (flatExportMode(exportOptions) ? "flat"
							: (zipExportMode(exportOptions) ? "zip" : "unknown")));
					LOG.info("  Locale to use: {}", locale(exportOptions)); // TODO: remove if not needed
					LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
					LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
					LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
				}

				Map<String, Table<Integer, Character, Object>> mapOfMatrices = exportEObjectsToMatrices(
						eObjects, exportOptions);

				if (flatExportMode(exportOptions)) {
					exportMatricesToCSVInFlatMode(outputStream, mapOfMatrices);

				} else if (zipExportMode(exportOptions)) {
					exportMatricesToCSVInZipMode(outputStream, mapOfMatrices);
				}
				
			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToCSVInZipMode(OutputStream outputStream,
			Map<String, Table<Integer, Character, Object>> mapOfMatrices) throws IOException {

		resetStopwatch();

		LOG.info("Starting generation of CSV files in ZIP mode");

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			for (String matrixName : mapOfMatrices.keySet()) {
				LOG.debug("Generating CSV file for matrix named '{}'", matrixName);

				Table<Integer, Character, Object> matrix = mapOfMatrices.get(matrixName);

				exportMatrixToCSVInZipMode(zipOutputStream, matrixName, matrix);
			}
		}

		LOG.info("Finished generation of CSV files in ZIP mode in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatrixToCSVInZipMode(ZipOutputStream zipOutputStream, String matrixName,
			Table<Integer, Character, Object> matrix) throws IOException {
		final StringWriter csvStringWriter = new StringWriter();

		try (CsvWriter csvWriter = CsvWriter.builder().build(csvStringWriter)) {
			writeCSVHeader(matrix, csvWriter);

			writeCSVData(matrix, csvWriter);

			writeZipEntry(zipOutputStream, matrixName, csvStringWriter);
		}
	}

	private void writeCSVHeader(Table<Integer, Character, Object> matrix, CsvWriter csvWriter) {
		Map<Character, Object> firstRow = matrix.row(Integer.valueOf(1));

		// @formatter:off
		List<String> firstRowValuesAsString = firstRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		csvWriter.writeRow(firstRowValuesAsString);
	}

	private void writeCSVData(Table<Integer, Character, Object> matrix, CsvWriter csvWriter) {
		Map<Integer, Map<Character, Object>> matrixRowMap = matrix.rowMap();

		// @formatter:off
		List<Integer> remainingRows = matrixRowMap.keySet()
				.stream()
				.skip(1)
				.collect(Collectors.toList());
		// @formatter:on

		for (Integer rowNumber : remainingRows) {
			Map<Character, Object> row = matrixRowMap.get(rowNumber);

			// @formatter:off
			List<String> rowValuesAsString = row.values()
					.stream()
					.map(v -> ((v instanceof Optional) ? "" : String.valueOf(v)))
					.collect(Collectors.toList());
			// @formatter:on						

			csvWriter.writeRow(rowValuesAsString);
		}
	}

	private void writeZipEntry(ZipOutputStream zipOutputStream, String matrixName, final StringWriter csvStringWriter)
			throws IOException {
		String zipEntryName = constructZipEntryName(matrixName);
		ZipEntry zipEntry = new ZipEntry(zipEntryName);
		zipOutputStream.putNextEntry(zipEntry);

		try (InputStream bais = new ByteArrayInputStream(csvStringWriter.toString().getBytes())) {
			byte[] bytes = new byte[1024];
			int length;
			while ((length = bais.read(bytes)) >= 0) {
				zipOutputStream.write(bytes, 0, length);
			}
		}

		zipOutputStream.closeEntry();
	}

	private void exportMatricesToCSVInFlatMode(OutputStream outputStream,
			Map<String, Table<Integer, Character, Object>> mapOfMatrices) throws IOException {

		try (PrintWriter printWriterOutputStream = new PrintWriter(outputStream)) {
			// TODO
		}

		throw new UnsupportedOperationException(
				"Export to CSV in flat mode is not supported yet - pending clarification");
	}

	private String constructZipEntryName(String matrixName) {
		String normalizedMatrixName = matrixName.strip().replaceAll("[()]", "").replaceAll("(?U)[^\\w\\._]+", "_");

		StringBuilder sb = new StringBuilder(100);
		sb.append(normalizedMatrixName);
		sb.append(".");
		sb.append(CSV_FILE_EXTENSION);
		return sb.toString();
	}

	@Override
	protected Map<Object, Object> validateExportOptions(Map<?, ?> options) throws EMFExportException {
		Map<Object, Object> exportOptions = super.validateExportOptions(options);

		if (!flatExportMode(exportOptions) && !zipExportMode(exportOptions)) {
			throw new EMFExportException("Please specify export mode!");
		}

		return exportOptions;
	}

	private boolean flatExportMode(Map<Object, Object> exportOptions) {
		return EMFCSVExportMode.valueOf(String.valueOf(exportOptions
				.getOrDefault(EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT))) == EMFCSVExportMode.FLAT;
	}

	private boolean zipExportMode(Map<Object, Object> exportOptions) {
		return EMFCSVExportMode.valueOf(String.valueOf(exportOptions
				.getOrDefault(EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP))) == EMFCSVExportMode.ZIP;
	}
}
