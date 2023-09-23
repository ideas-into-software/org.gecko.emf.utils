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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExportOptions;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.cells.EMFExportEObjectIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.csv.api.EMFCSVExportMode;
import org.gecko.emf.exporter.csv.api.EMFCSVExportOptions;
import org.gecko.emf.exporter.headers.EMFExportEObjectColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectGenericColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectIDColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectManyReferencesColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectOneReferenceColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectReferenceColumnHeader;
import org.gecko.emf.exporter.keys.EMFExportColumnHasValueCompositeKey;
import org.gecko.emf.exporter.keys.EMFExportRefHasValueCompositeKey;
import org.gecko.emf.exporter.keys.EMFExportRefMatrixNameIDCompositeKey;
import org.gecko.emf.exporter.keys.EMFExportRefsMaxValueCountCompositeKey;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import de.siegmar.fastcsv.writer.CsvWriter;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting
 * EMF resources and lists of EMF objects to CSV format.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFCSVExporter", scope = ServiceScope.PROTOTYPE)
public class EMFCSVExporter extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFCSVExporter.class);

	private static final String CSV_FILE_EXTENSION = "csv";

	// lookup index for storing information if specific one-to-one reference has
	// value
	protected final Map<EMFExportRefHasValueCompositeKey, Boolean> refHasValueIndex;

	// lookup index for storing information about max number of references for
	// specific one-to-many reference
	protected final Map<EMFExportRefsMaxValueCountCompositeKey, Integer> refsMaxValueCountIndex;

	// lookup index for storing information if specific column has value
	protected final Map<EMFExportColumnHasValueCompositeKey, Boolean> columnHasValueIndex;

	public EMFCSVExporter() {
		super(LOG, Stopwatch.createStarted());

		this.refsMaxValueCountIndex = new HashMap<>();
		this.refHasValueIndex = new HashMap<>();
		this.columnHasValueIndex = new HashMap<>();
	}

	/*
	 * Depending on export mode used (FLAT, ZIP), 'java.io.OutputStream' passed is
	 * either a file or a ZIP archive to which files are collected.
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
				resetState();

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

				exportMatricesToCSV(outputStream, eObjects, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToCSV(OutputStream outputStream, List<EObject> eObjects,
			Map<Object, Object> exportOptions) throws IOException, EMFExportException {

		if (flatExportMode(exportOptions)) {
			exportMatricesToCSVInFlatMode(outputStream, eObjects, exportOptions);

		} else if (zipExportMode(exportOptions)) {
			exportMatricesToCSVInZipMode(outputStream, eObjects, exportOptions);
		}
	}

	private void exportMatricesToCSVInZipMode(OutputStream outputStream, List<EObject> eObjects,
			Map<Object, Object> exportOptions) throws EMFExportException {

		Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap = exportEObjectsToMatrices(eObjects,
				exportOptions);

		resetStopwatch();

		LOG.info("Starting generation of CSV files in ZIP mode");

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			for (String matrixName : matrixNameToMatrixMap.keySet()) {
				LOG.debug("Generating CSV file for matrix named '{}'", matrixName);

				Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

				exportMatrixToCSVInZipMode(zipOutputStream, matrixName, matrix);
			}
		} catch (IOException e) {
			throw new EMFExportException(e);
		}

		LOG.info("Finished generation of CSV files in ZIP mode in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToCSVInFlatMode(OutputStream outputStream, List<EObject> eObjects,
			Map<Object, Object> exportOptions) throws IOException, EMFExportException {

		// validate if objects from list passed share the same hierarchy - this is only
		// necessary for flat export mode, there are no such restrictions for "regular"
		// mode
		validateClassHierarchyForRootObjects(eObjects);

		Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap = exportEObjectsToMatrices(eObjects,
				exportOptions);

		resetStopwatch();

		LOG.info("Starting generation of CSV files in flat mode");

		Map<String, Table<Integer, Integer, Object>> eObjectMatrixNameToMatrixMap = eObjectMatricesOnly(
				matrixNameToMatrixMap);

		Table<Integer, Integer, Object> flatMatrix = HashBasedTable.create();

		// skip matrices whose structural features are unpacked when constructing one
		// reference / many references column headers (e.g. "Filter_Id" vs
		// "filtering.Id") to avoid duplication of information presented
		Set<String> nonRefMatrixNames = nonRefMatrixNames(eObjectMatrixNameToMatrixMap);

		constructFlatMatrixColumnHeaders(eObjectMatrixNameToMatrixMap, nonRefMatrixNames, flatMatrix, exportOptions);

		validateMatrixColumnsSize(flatMatrix, "flat matrix");

		populateFlatMatrixWithData(eObjectMatrixNameToMatrixMap, nonRefMatrixNames, flatMatrix, exportOptions);

		try (PrintWriter printWriterOutputStream = new PrintWriter(outputStream)) {

			try (CsvWriter csvWriter = CsvWriter.builder().build(printWriterOutputStream)) {

				writeCSVHeader(flatMatrix, csvWriter);

				writeCSVData(flatMatrix, csvWriter);
			}
		}

		LOG.info("Finished generation of CSV files in flat mode in {} second(s)", elapsedTimeInSeconds());
	}

	private void constructFlatMatrixColumnHeaders(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> nonRefMatrixNames, Table<Integer, Integer, Object> flatMatrix,
			Map<Object, Object> exportOptions) throws EMFExportException {
		AtomicInteger flatMatrixColumnKey = new AtomicInteger(1);

		for (String nonRefMatrixName : nonRefMatrixNames) {
			constructFlatMatrixColumnHeaders(matrixNameToMatrixMap, flatMatrix, exportOptions, flatMatrixColumnKey,
					nonRefMatrixName);
		}
	}

	private void constructFlatMatrixColumnHeaders(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, Map<Object, Object> exportOptions,
			AtomicInteger flatMatrixColumnKey, String nonRefMatrixName) throws EMFExportException {
		Table<Integer, Integer, Object> nonRefMatrix = matrixNameToMatrixMap.get(nonRefMatrixName);

		Map<Integer, Object> firstNonRefMatrixRow = nonRefMatrix.row(getMatrixRowKey(1));

		for (Map.Entry<Integer, Object> firstNonRefMatrixRowColumn : firstNonRefMatrixRow.entrySet()) {

			if (exportNonContainmentEnabled(exportOptions)) {

				// @formatter:off
				constructFlatMatrixColumnHeaders(
						matrixNameToMatrixMap, 
						flatMatrix, 
						flatMatrixColumnKey,
						nonRefMatrix,
						nonRefMatrixName,
						(EMFExportEObjectColumnHeader) firstNonRefMatrixRowColumn.getValue(),
						firstNonRefMatrixRowColumn.getKey(),
						firstNonRefMatrixRowColumn.getValue());
				// @formatter:on

				// when export non-containment references is disabled, we do not unpack
				// non-containment references, we process those directly and output their
				// identifiers only
			} else {

				// @formatter:off
				constructFlatMatrixColumnHeader(
						matrixNameToMatrixMap, 
						flatMatrix, 
						flatMatrixColumnKey,
						(EMFExportEObjectColumnHeader) firstNonRefMatrixRowColumn.getValue(), 
						firstNonRefMatrixRowColumn.getValue());
				// @formatter:on				
			}
		}
	}

	private void constructFlatMatrixColumnHeaders(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			Table<Integer, Integer, Object> matrix, String matrixName,
			EMFExportEObjectColumnHeader nonRefMatrixColumnHeader, Integer matrixRowColumnKey, Object rawColumnHeader,
			String... columnHeaderNameParts) throws EMFExportException {

		if ((rawColumnHeader instanceof EMFExportEObjectOneReferenceColumnHeader) && matrixNameToMatrixMap
				.containsKey(((EMFExportEObjectOneReferenceColumnHeader) rawColumnHeader).getRefMatrixName())) {

			// @formatter:off
			constructFlatMatrixOneReferenceColumnHeaders(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey,
					nonRefMatrixColumnHeader, 
					rawColumnHeader, 
					columnHeaderNameParts);
			// @formatter:on

		} else if ((rawColumnHeader instanceof EMFExportEObjectManyReferencesColumnHeader) && matrixNameToMatrixMap
				.containsKey(((EMFExportEObjectManyReferencesColumnHeader) rawColumnHeader).getRefMatrixName())) {

			// @formatter:off
			constructFlatMatrixManyReferencesColumnHeaders(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey,
					matrix, 
					nonRefMatrixColumnHeader, 
					matrixRowColumnKey, 
					rawColumnHeader, 
					columnHeaderNameParts);
			// @formatter:on

		} else if ((rawColumnHeader instanceof EMFExportEObjectIDColumnHeader)
				|| (rawColumnHeader instanceof EMFExportEObjectGenericColumnHeader)) {

			// @formatter:off
			constructFlatMatrixColumnHeader(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey,
					nonRefMatrixColumnHeader, 
					rawColumnHeader);
			// @formatter:on

		} else {
			throw new EMFExportException("Unrecognized column type!");
		}
	}

	private void constructFlatMatrixColumnHeader(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			EMFExportEObjectColumnHeader nonRefMatrixColumnHeader, Object rawColumnHeader) throws EMFExportException {

		boolean hasValue = hasValue(matrixNameToMatrixMap, nonRefMatrixColumnHeader,
				(EMFExportEObjectColumnHeader) rawColumnHeader);

		if (hasValue) {
			// @formatter:off
			constructFlatMatrixColumnHeader(
					flatMatrix,
					constructFlatMatrixColumnHeaderName( (EMFExportEObjectColumnHeader) rawColumnHeader ), 
					flatMatrixColumnKey);
			// @formatter:on
		}
	}

	private void constructFlatMatrixOneReferenceColumnHeaders(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			EMFExportEObjectColumnHeader nonRefMatrixColumnHeader, Object rawColumnHeader,
			String... columnHeaderNameParts) throws EMFExportException {

		boolean refHasValue = refHasValue(matrixNameToMatrixMap, nonRefMatrixColumnHeader, rawColumnHeader);

		if (refHasValue) {

			EMFExportEObjectOneReferenceColumnHeader oneReferenceColumnHeader = (EMFExportEObjectOneReferenceColumnHeader) rawColumnHeader;

			String columnHeaderName = oneReferenceColumnHeader.getColumnHeaderName().replaceFirst(REF_COLUMN_PREFIX,
					"");

			String refMatrixName = oneReferenceColumnHeader.getRefMatrixName();

			Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

			Map<Integer, Object> firstRefMatrixRow = refMatrix.row(getMatrixRowKey(1));

			for (Map.Entry<Integer, Object> firstRefMatrixRowColumn : firstRefMatrixRow.entrySet()) {

				Object firstRefMatrixRowColumnValue = firstRefMatrixRowColumn.getValue();

				// this check is needed to avoid infinite loops in case of self-referencing
				// models
				if (firstRefMatrixRowColumnValue instanceof EMFExportEObjectReferenceColumnHeader && !refMatrixName
						.equalsIgnoreCase(((EMFExportEObjectReferenceColumnHeader) firstRefMatrixRowColumnValue)
								.getRefMatrixName())) {

					// @formatter:off
					constructFlatMatrixColumnHeaders(
							matrixNameToMatrixMap, 
							flatMatrix, 
							flatMatrixColumnKey, 
							refMatrix,
							refMatrixName,
							nonRefMatrixColumnHeader,
							firstRefMatrixRowColumn.getKey(),
							firstRefMatrixRowColumn.getValue(), 
							columnHeaderName);
					// @formatter:on

				} else {

					boolean refFeatureHasValue = refHasValue(matrixNameToMatrixMap, oneReferenceColumnHeader,
							(EMFExportEObjectColumnHeader) firstRefMatrixRowColumnValue);

					if (refFeatureHasValue) {

						// @formatter:off
						constructFlatMatrixColumnHeader(flatMatrix,
								constructFlatMatrixReferenceColumnHeaderName(
										constructFlatMatrixReferenceColumnHeaderNameParts(Arrays.asList(columnHeaderNameParts), 
												columnHeaderName, ((EMFExportEObjectColumnHeader) firstRefMatrixRowColumnValue).getColumnHeaderName())),
								flatMatrixColumnKey);
						// @formatter:on
					}
				}
			}
		}
	}

	private void constructFlatMatrixManyReferencesColumnHeaders(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			Table<Integer, Integer, Object> matrix, EMFExportEObjectColumnHeader nonRefMatrixColumnHeader,
			Integer matrixRowColumnKey, Object rawColumnHeader, String... columnHeaderNameParts)
			throws EMFExportException {

		int refsMaxValueCount = findRefsMaxValueCount(matrixNameToMatrixMap, matrix, nonRefMatrixColumnHeader,
				matrixRowColumnKey, rawColumnHeader);

		if (refsMaxValueCount > 0) {

			EMFExportEObjectManyReferencesColumnHeader manyReferencesColumnHeader = (EMFExportEObjectManyReferencesColumnHeader) rawColumnHeader;

			String columnHeaderName = manyReferencesColumnHeader.getColumnHeaderName().replaceFirst(REF_COLUMN_PREFIX,
					"");

			String refMatrixName = manyReferencesColumnHeader.getRefMatrixName();

			Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

			for (int colIndex = 0; colIndex < refsMaxValueCount; colIndex++) {

				Map<Integer, Object> firstRefMatrixRow = refMatrix.row(getMatrixRowKey(1));

				for (Map.Entry<Integer, Object> firstRefMatrixRowColumn : firstRefMatrixRow.entrySet()) {

					Object firstRefMatrixRowColumnValue = firstRefMatrixRowColumn.getValue();

					// this check is needed to avoid infinite loops in case of self-referencing
					// models
					if (firstRefMatrixRowColumnValue instanceof EMFExportEObjectReferenceColumnHeader && !refMatrixName
							.equalsIgnoreCase(((EMFExportEObjectReferenceColumnHeader) firstRefMatrixRowColumnValue)
									.getRefMatrixName())) {

						// @formatter:off
						constructFlatMatrixColumnHeaders(
								matrixNameToMatrixMap, 
								flatMatrix, 
								flatMatrixColumnKey, 
								refMatrix,
								refMatrixName,
								nonRefMatrixColumnHeader,
								firstRefMatrixRowColumn.getKey(),
								firstRefMatrixRowColumn.getValue(), 
								constructFlatMatrixReferenceColumnHeaderName(
										constructFlatMatrixReferenceColumnHeaderNameParts(
												Arrays.asList(columnHeaderNameParts), columnHeaderName,
												String.valueOf(colIndex))));
						// @formatter:on

					} else {

						boolean refFeatureHasValue = refHasValue(matrixNameToMatrixMap, manyReferencesColumnHeader,
								(EMFExportEObjectColumnHeader) firstRefMatrixRowColumnValue);

						if (refFeatureHasValue) {

							// @formatter:off
							constructFlatMatrixColumnHeader(
									flatMatrix,
									constructFlatMatrixReferenceColumnHeaderName(
											constructFlatMatrixReferenceColumnHeaderNameParts(
													Arrays.asList(columnHeaderNameParts), columnHeaderName,
													String.valueOf(colIndex),
													((EMFExportEObjectColumnHeader) firstRefMatrixRowColumnValue)
													.getColumnHeaderName().replaceFirst(REF_COLUMN_PREFIX, ""))), 	
									flatMatrixColumnKey);
							// @formatter:on
						}
					}
				}
			}
		}
	}

	private void constructFlatMatrixColumnHeader(Table<Integer, Integer, Object> flatMatrix, String columnHeaderName,
			AtomicInteger flatMatrixColumnKey) {

		LOG.debug("Constructing column header name '{}'", columnHeaderName);

		flatMatrix.put(getMatrixRowKey(1), getMatrixColumnKey(flatMatrixColumnKey.getAndIncrement()), columnHeaderName);
	}

	private String constructFlatMatrixColumnHeaderName(EMFExportEObjectColumnHeader columnHeader) {
		if (!(columnHeader.getColumnHeaderName()).equalsIgnoreCase(ID_COLUMN_NAME)) {
			return columnHeader.getColumnHeaderName();
		}

		StringBuilder sb = new StringBuilder(100);
		sb.append(columnHeader.getMatrixName());
		sb.append("_");
		sb.append(ID_COLUMN_NAME);

		return sb.toString();
	}

	private String constructFlatMatrixReferenceColumnHeaderName(String... columnHeaderNameParts) {
		return String.join(".", columnHeaderNameParts);
	}

	private String[] constructFlatMatrixReferenceColumnHeaderNameParts(List<String> existingParts, String... newParts) {
		List<String> parts = new ArrayList<>();
		parts.addAll(existingParts);
		parts.addAll(Arrays.asList(newParts));
		return parts.toArray(new String[parts.size()]);
	}

	private void populateFlatMatrixWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> nonRefMatrixNames, Table<Integer, Integer, Object> flatMatrix,
			Map<Object, Object> exportOptions) throws EMFExportException {

		// in flat mode, we only process non-ref matrices
		for (String nonRefMatrixName : nonRefMatrixNames) {
			populateFlatMatrixWithData(matrixNameToMatrixMap, flatMatrix, exportOptions, nonRefMatrixName);
		}
	}

	private void populateFlatMatrixWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, Map<Object, Object> exportOptions, String nonRefMatrixName)
			throws EMFExportException {

		Table<Integer, Integer, Object> nonRefMatrix = matrixNameToMatrixMap.get(nonRefMatrixName);

		Map<Integer, Map<Integer, Object>> nonRefMatrixRowMap = nonRefMatrix.rowMap();

		// @formatter:off
		List<Integer> remainingNonRefMatrixRowNumbers = nonRefMatrixRowMap.keySet()
				.stream()
				.skip(1)
				.collect(Collectors.toList());
		// @formatter:on

		for (Integer nonRefMatrixRowNumber : remainingNonRefMatrixRowNumbers) {
			Map<Integer, Object> nonRefMatrixRow = nonRefMatrixRowMap.get(nonRefMatrixRowNumber);

			AtomicInteger flatMatrixColumnKey = new AtomicInteger(1);

			for (Map.Entry<Integer, Object> nonRefMatrixRowColumn : nonRefMatrixRow.entrySet()) {

				Object rawNonRefMatrixColumnHeader = nonRefMatrix.get(getMatrixRowKey(1),
						getMatrixColumnKey(nonRefMatrixRowColumn.getKey()));

				if (exportNonContainmentEnabled(exportOptions)) {

					// @formatter:off
					populateFlatMatrixWithData(
							matrixNameToMatrixMap, 
							flatMatrix, 
							flatMatrixColumnKey, 
							nonRefMatrix, 
							nonRefMatrixName,
							nonRefMatrixRowNumber, 
							(EMFExportEObjectColumnHeader) rawNonRefMatrixColumnHeader,
							null,
							nonRefMatrixRowColumn.getKey(),
							nonRefMatrixRowColumn.getValue());
					// @formatter:on

					// when export non-containment references is disabled, we do not unpack
					// non-containment references, we process those directly and output their
					// identifiers only
				} else {

					// @formatter:off
					populateFlatMatrixColumnWithData(
							matrixNameToMatrixMap, 
							flatMatrix, 
							flatMatrixColumnKey,
							nonRefMatrixRowNumber, 
							(EMFExportEObjectColumnHeader) rawNonRefMatrixColumnHeader,
							null, 
							nonRefMatrixRowColumn.getValue());
					// @formatter:on
				}
			}
		}
	}

	private void populateFlatMatrixWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			Table<Integer, Integer, Object> matrix, String matrixName, Integer matrixRowNumber,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader,
			Integer matrixRowColumnKey, Object rawMatrixRowColumnValue) throws EMFExportException {

		if ((rawMatrixRowColumnValue instanceof EMFExportEObjectOneReferenceValueCell)
				&& matrixNameToMatrixMap.containsKey(
						((EMFExportEObjectOneReferenceValueCell) rawMatrixRowColumnValue).getRefMatrixName())
				&& !matrixName.equalsIgnoreCase(
						((EMFExportEObjectOneReferenceValueCell) rawMatrixRowColumnValue).getRefMatrixName())) {

			// @formatter:off
			populateFlatMatrixOneReferenceColumnWithData(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey, 
					matrix,
					matrixRowNumber, 
					matrixColumnHeader, 
					refMatrixColumnHeader,
					matrixRowColumnKey, 
					rawMatrixRowColumnValue);
			// @formatter:on

		} else if ((rawMatrixRowColumnValue instanceof EMFExportEObjectManyReferencesValueCell)
				&& matrixNameToMatrixMap.containsKey(
						((EMFExportEObjectManyReferencesValueCell) rawMatrixRowColumnValue).getRefMatrixName())
				&& !matrixName.equalsIgnoreCase(
						((EMFExportEObjectManyReferencesValueCell) rawMatrixRowColumnValue).getRefMatrixName())) {

			// @formatter:off
			populateFlatMatrixManyReferencesColumnWithData(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey,
					matrix, 
					matrixRowNumber, 
					matrixColumnHeader, 
					refMatrixColumnHeader,
					matrixRowColumnKey, 
					rawMatrixRowColumnValue);
			// @formatter:on

		} else {

			// @formatter:off
			populateFlatMatrixColumnWithData(
					matrixNameToMatrixMap, 
					flatMatrix, 
					flatMatrixColumnKey,
					matrixRowNumber, 
					matrixColumnHeader, 
					refMatrixColumnHeader, 
					rawMatrixRowColumnValue);
			// @formatter:on
		}
	}

	private void populateFlatMatrixColumnWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey, Integer matrixRowNumber,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader,
			Object rawMatrixRowColumnValue) throws EMFExportException {

		boolean hasValue = hasValue(matrixNameToMatrixMap, matrixColumnHeader, refMatrixColumnHeader);

		if (hasValue) {
			populateFlatMatrixColumnWithData(flatMatrix, flatMatrixColumnKey, matrixRowNumber, rawMatrixRowColumnValue);
		}
	}

	private void populateFlatMatrixColumnWithData(Table<Integer, Integer, Object> flatMatrix,
			AtomicInteger flatMatrixColumnKey, Integer rowKey, Object value) {

		if ((value == null) || (value instanceof Optional)) {
			LOG.debug("Inserting EMPTY value cell at row '{}' and column '{}'", rowKey,
					flatMatrix.get(getMatrixRowKey(1), getMatrixColumnKey(flatMatrixColumnKey.get())));

			flatMatrix.put(rowKey, getMatrixColumnKey(flatMatrixColumnKey.getAndIncrement()), Optional.empty());

		} else {

			LOG.debug("Inserting value cell at row '{}' and column '{}'", rowKey,
					flatMatrix.get(getMatrixRowKey(1), getMatrixColumnKey(flatMatrixColumnKey.get())));

			flatMatrix.put(rowKey, getMatrixColumnKey(flatMatrixColumnKey.getAndIncrement()), value);
		}
	}

	private void populateFlatMatrixOneReferenceColumnWithData(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			Table<Integer, Integer, Object> matrix, Integer matrixRowNumber,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader,
			Integer matrixRowColumnKey, Object rawMatrixRowColumnValue) throws EMFExportException {

		String refMatrixName = ((EMFExportEObjectOneReferenceValueCell) rawMatrixRowColumnValue).getRefMatrixName();

		Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

		if (((EMFExportEObjectOneReferenceValueCell) rawMatrixRowColumnValue).hasValue()) {

			String refID = ((EMFExportEObjectOneReferenceValueCell) rawMatrixRowColumnValue).getValue();

			int refMatrixRowKey = findRefMatrixRowKey(refMatrixName, refID);

			if (refMatrixRowKey != -1) {

				Map<Integer, Object> refMatrixRow = refMatrix.row(getMatrixRowKey(refMatrixRowKey));

				for (Map.Entry<Integer, Object> refMatrixRowColumn : refMatrixRow.entrySet()) {

					// @formatter:off
					populateFlatMatrixWithData(
							matrixNameToMatrixMap,
							flatMatrix,
							flatMatrixColumnKey, 
							refMatrix,
							refMatrixName,
							matrixRowNumber, 
							(refMatrixColumnHeader != null) ? refMatrixColumnHeader : matrixColumnHeader,
							(EMFExportEObjectColumnHeader) refMatrix.get(getMatrixRowKey(1), getMatrixColumnKey(refMatrixRowColumn.getKey())),
							refMatrixRowColumn.getKey(),
							refMatrixRowColumn.getValue());
					// @formatter:on
				}
			}

			// even if there is no value for that particular ref, column may already be
			// present if other rows have value in that column, so at least empty cell(s)
			// must be created
		} else {

			boolean refHasValue = refHasValue(matrixNameToMatrixMap, matrixColumnHeader, refMatrixColumnHeader);

			if (refHasValue) {

				for (Map.Entry<Integer, Object> firstRefMatrixRowColumn : refMatrix.row(getMatrixRowKey(1))
						.entrySet()) {

					// @formatter:off
					boolean refFeatureHasValue = refHasValue(
							matrixNameToMatrixMap,
							matrix,
							matrixRowColumnKey,
							refMatrix,
							firstRefMatrixRowColumn.getKey());
					// @formatter:on

					if (refFeatureHasValue) {
						populateFlatMatrixColumnWithData(flatMatrix, flatMatrixColumnKey, matrixRowNumber,
								Optional.empty());
					}
				}
			}
		}
	}

	private void populateFlatMatrixManyReferencesColumnWithData(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> flatMatrix, AtomicInteger flatMatrixColumnKey,
			Table<Integer, Integer, Object> matrix, Integer matrixRowNumber,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader,
			Integer matrixRowColumnKey, Object rawMatrixRowColumnValue) throws EMFExportException {

		String refMatrixName = ((EMFExportEObjectManyReferencesValueCell) rawMatrixRowColumnValue).getRefMatrixName();

		Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

		if (((EMFExportEObjectManyReferencesValueCell) rawMatrixRowColumnValue).hasValues()) {

			List<String> refIDs = ((EMFExportEObjectManyReferencesValueCell) rawMatrixRowColumnValue).getValues();

			for (String refID : refIDs) {

				int refMatrixRowKey = findRefMatrixRowKey(refMatrixName, refID);

				if (refMatrixRowKey != -1) {

					Map<Integer, Object> refMatrixRow = refMatrix.row(getMatrixRowKey(refMatrixRowKey));

					for (Map.Entry<Integer, Object> refMatrixRowColumn : refMatrixRow.entrySet()) {

						// @formatter:off
						populateFlatMatrixWithData(
								matrixNameToMatrixMap,
								flatMatrix,
								flatMatrixColumnKey, 
								refMatrix,
								refMatrixName,
								matrixRowNumber, 
								(refMatrixColumnHeader != null) ? refMatrixColumnHeader : matrixColumnHeader,
								(EMFExportEObjectColumnHeader) refMatrix.get(getMatrixRowKey(1), getMatrixColumnKey(refMatrixRowColumn.getKey())),
								refMatrixRowColumn.getKey(),
								refMatrixRowColumn.getValue());
						// @formatter:on
					}
				}
			}

			// even if there is no value for that particular ref, column may already be
			// present if other rows have value in that column, so at least empty cell(s)
			// must be created
		} else {

			// @formatter:off
			int manyReferencesMaxValueCount = findRefsMaxValueCount(
					matrixNameToMatrixMap,
					matrixColumnHeader, 
					matrix,
					matrixRowColumnKey);
			// @formatter:on

			if (manyReferencesMaxValueCount > 0) {

				for (int emptyRefId = 0; emptyRefId <= manyReferencesMaxValueCount; emptyRefId++) {

					for (Map.Entry<Integer, Object> firstRefMatrixRowColumn : refMatrix.row(getMatrixRowKey(1))
							.entrySet()) {

						// @formatter:off
						boolean refFeatureHasValue = refHasValue(
								matrixNameToMatrixMap,
								matrix,
								matrixRowColumnKey,
								refMatrix,
								firstRefMatrixRowColumn.getKey());
						// @formatter:on

						if (refFeatureHasValue) {
							populateFlatMatrixColumnWithData(flatMatrix, flatMatrixColumnKey, matrixRowNumber,
									Optional.empty());
						}
					}
				}
			}
		}
	}

	private void exportMatrixToCSVInZipMode(ZipOutputStream zipOutputStream, String matrixName,
			Table<Integer, Integer, Object> matrix) throws IOException {
		final StringWriter csvStringWriter = new StringWriter();

		try (CsvWriter csvWriter = CsvWriter.builder().build(csvStringWriter)) {

			writeCSVHeader(matrix, csvWriter);

			writeCSVData(matrix, csvWriter);

			writeZipEntry(zipOutputStream, matrixName, csvStringWriter);
		}
	}

	private void writeCSVHeader(Table<Integer, Integer, Object> matrix, CsvWriter csvWriter) {
		Map<Integer, Object> firstRow = matrix.row(getMatrixRowKey(1));

		// @formatter:off
		List<String> firstRowValuesAsString = firstRow.values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on

		csvWriter.writeRow(firstRowValuesAsString);
	}

	private void writeCSVData(Table<Integer, Integer, Object> matrix, CsvWriter csvWriter) {
		Map<Integer, Map<Integer, Object>> matrixRowMap = matrix.rowMap();

		// @formatter:off
		List<Integer> remainingRows = matrixRowMap.keySet()
				.stream()
				.skip(1)
				.collect(Collectors.toList());
		// @formatter:on

		for (Integer rowNumber : remainingRows) {
			Map<Integer, Object> row = matrixRowMap.get(rowNumber);

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

	private String constructZipEntryName(String matrixName) {
		String normalizedMatrixName = matrixName.strip().replaceAll("[()]", "").replaceAll("(?U)[^\\w\\._]+", "_");

		StringBuilder sb = new StringBuilder(100);
		sb.append(normalizedMatrixName);
		sb.append(".");
		sb.append(CSV_FILE_EXTENSION);
		return sb.toString();
	}

	@SuppressWarnings("unused") // TODO: remove if not needed
	private int findRefMatrixRowKey(String refID) {
		if (eObjectIDToMatrixNameMap.containsKey(refID)) {
			return findRefMatrixRowKey(eObjectIDToMatrixNameMap.get(refID), refID);
		} else {
			return -1;
		}
	}

	private int findRefMatrixRowKey(String refMatrixName, String refID) {
		EMFExportRefMatrixNameIDCompositeKey refMatrixNameIDCompositeKey = new EMFExportRefMatrixNameIDCompositeKey(
				refMatrixName, refID);

		Integer refMatrixRowKey = refMatrixRowKeyIndex.get(refMatrixNameIDCompositeKey);
		if (refMatrixRowKey != null) {
			return refMatrixRowKey;
		} else {
			return -1;
		}
	}

	private int findRefsMaxValueCount(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> matrix, EMFExportEObjectColumnHeader nonRefMatrixColumnHeader,
			Integer matrixRowColumnKey, Object refMatrixColumnHeader) throws EMFExportException {

		int refsMaxValueCount = 0;

		if (refMatrixColumnHeader != nonRefMatrixColumnHeader
				&& (nonRefMatrixColumnHeader instanceof EMFExportEObjectReferenceColumnHeader)
				&& (refMatrixColumnHeader instanceof EMFExportEObjectReferenceColumnHeader)
				&& (((EMFExportEObjectReferenceColumnHeader) nonRefMatrixColumnHeader).getRefMatrixName())
						.equalsIgnoreCase(
								((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getMatrixName())) {

			refsMaxValueCount = findRefsMaxValueCount(matrixNameToMatrixMap,
					((EMFExportEObjectReferenceColumnHeader) nonRefMatrixColumnHeader).getMatrixName(),
					((EMFExportEObjectReferenceColumnHeader) nonRefMatrixColumnHeader).getColumnHeaderName(),
					((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getMatrixName(),
					((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getColumnHeaderName());

		} else {
			refsMaxValueCount = findRefsMaxValueCount(matrix, matrixRowColumnKey);

		}

		return refsMaxValueCount;
	}

	private int findRefsMaxValueCount(Table<Integer, Integer, Object> matrix, Integer matrixColIndex) {
		int refsMaxValueCount = 0;

		Map<Integer, Object> matrixColumn = matrix.column(matrixColIndex);
		for (Map.Entry<Integer, Object> matrixColumnRow : matrixColumn.entrySet()) {
			if (matrixColumnRow.getValue() instanceof EMFExportEObjectManyReferencesValueCell) {
				refsMaxValueCount = Math.max(refsMaxValueCount,
						((EMFExportEObjectManyReferencesValueCell) matrixColumnRow.getValue()).getValuesCount());
			}
		}
		return refsMaxValueCount;
	}

	private int findRefsMaxValueCount(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EMFExportEObjectColumnHeader nonRefMatrixColumnHeader, Table<Integer, Integer, Object> matrix,
			Integer matrixRowColumnKey) throws EMFExportException {

		Object refMatrixColumnHeader = matrix.get(getMatrixRowKey(1), matrixRowColumnKey);

		return findRefsMaxValueCount(matrixNameToMatrixMap,
				((EMFExportEObjectReferenceColumnHeader) nonRefMatrixColumnHeader).getMatrixName(),
				((EMFExportEObjectReferenceColumnHeader) nonRefMatrixColumnHeader).getColumnHeaderName(),
				((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getMatrixName(),
				((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getColumnHeaderName());
	}

	private int findRefsMaxValueCount(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			String matrixName, String columnHeaderName, String refMatrixName, String refColumnHeaderName)
			throws EMFExportException {

		EMFExportRefsMaxValueCountCompositeKey refsMaxValueCountCompositeKey = new EMFExportRefsMaxValueCountCompositeKey(
				matrixName, columnHeaderName, refMatrixName, refColumnHeaderName);
		if (refsMaxValueCountIndex.containsKey(refsMaxValueCountCompositeKey)) {
			return refsMaxValueCountIndex.get(refsMaxValueCountCompositeKey);
		}

		int refsMaxValueCount = 0;

		if (matrixNameToMatrixMap.containsKey(matrixName) && matrixNameToMatrixMap.containsKey(refMatrixName)) {

			Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

			Map<Integer, Object> firstMatrixRow = matrix.row(getMatrixRowKey(1));

			Integer matrixColumnKey = findMatrixColumnKey(columnHeaderName, firstMatrixRow);
			if (matrixColumnKey == null) {
				throw new EMFExportException(String.format("Column named %s does not exist in matrix named %s!",
						columnHeaderName, matrixName));
			}

			Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

			Map<Integer, Object> firstRefMatrixRow = refMatrix.row(getMatrixRowKey(1));

			Integer refMatrixColumnKey = findMatrixColumnKey(refColumnHeaderName, firstRefMatrixRow);
			if (refMatrixColumnKey == null) {
				throw new EMFExportException(String.format("Column named %s does not exist in matrix named %s!",
						refColumnHeaderName, refMatrixName));
			}

			List<String> refIDs = findRefIDs(matrix, matrixColumnKey);

			if (!refIDs.isEmpty()) {

				for (String refID : refIDs) {

					int refMatrixRowKey = findRefMatrixRowKey(refMatrixName, refID);

					if (refMatrixRowKey != -1) {

						Object refMatrixColumnRowValue = refMatrix.get(getMatrixRowKey(refMatrixRowKey),
								refMatrixColumnKey);

						if (refMatrixColumnRowValue instanceof EMFExportEObjectManyReferencesValueCell) {
							refsMaxValueCount = Math.max(refsMaxValueCount,
									((EMFExportEObjectManyReferencesValueCell) refMatrixColumnRowValue)
											.getValuesCount());
						}
					}
				}
			}
		}

		refsMaxValueCountIndex.put(refsMaxValueCountCompositeKey, getMatrixColumnKey(refsMaxValueCount));

		return refsMaxValueCount;
	}

	private boolean refHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> matrix, Integer matrixRowColumnKey,
			Table<Integer, Integer, Object> refMatrix, Integer refMatrixRowColumnKey) throws EMFExportException {

		Object matrixColumnHeader = matrix.get(getMatrixRowKey(1), matrixRowColumnKey);
		if (matrixColumnHeader == null) {
			throw new EMFExportException(String.format("Column header does not exist at column %s in matrix named %s!",
					matrixRowColumnKey, matrix));
		}

		Object refMatrixColumnHeader = refMatrix.get(getMatrixRowKey(1), refMatrixRowColumnKey);
		if (refMatrixColumnHeader == null) {
			throw new EMFExportException(String.format("Column header does not exist at column %s in matrix named %s!",
					refMatrixRowColumnKey, refMatrix));
		}

		return refHasValue(matrixNameToMatrixMap, (EMFExportEObjectColumnHeader) matrixColumnHeader,
				(EMFExportEObjectColumnHeader) refMatrixColumnHeader);
	}

	// TODO: decide if this should be used at all / if checks should be directly in
	// #refHasValue(Map<String, Table<Integer, Integer, Object>>, String, String,
	// String, String)
	private boolean refHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EMFExportEObjectColumnHeader matrixColumnHeader, Object refMatrixColumnHeader) throws EMFExportException {

		boolean refHasValue = true;

		if (refMatrixColumnHeader != matrixColumnHeader
				&& (matrixColumnHeader instanceof EMFExportEObjectReferenceColumnHeader)
				&& (refMatrixColumnHeader instanceof EMFExportEObjectReferenceColumnHeader)
				&& (((EMFExportEObjectReferenceColumnHeader) matrixColumnHeader).getRefMatrixName()).equalsIgnoreCase(
						((EMFExportEObjectReferenceColumnHeader) refMatrixColumnHeader).getMatrixName())) {

			refHasValue = refHasValue(matrixNameToMatrixMap, matrixColumnHeader,
					(EMFExportEObjectColumnHeader) refMatrixColumnHeader);
		}

		return refHasValue;
	}

	private boolean refHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader)
			throws EMFExportException {

		if (matrixColumnHeader == null) {
			throw new EMFExportException(String.format("Matrix column header is required!"));
		}

		if (refMatrixColumnHeader == null) {
			throw new EMFExportException(String.format("RefMatrix column header is required!"));
		}

		return refHasValue(matrixNameToMatrixMap, ((EMFExportEObjectColumnHeader) matrixColumnHeader).getMatrixName(),
				((EMFExportEObjectColumnHeader) matrixColumnHeader).getColumnHeaderName(),
				((EMFExportEObjectColumnHeader) refMatrixColumnHeader).getMatrixName(),
				((EMFExportEObjectColumnHeader) refMatrixColumnHeader).getColumnHeaderName());
	}

	private boolean refHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, String matrixName,
			String columnHeaderName, String refMatrixName, String refColumnHeaderName) throws EMFExportException {

		boolean refHasValue = true;

		if (!matrixName.equalsIgnoreCase(refMatrixName)) {

			EMFExportRefHasValueCompositeKey refHasValueCompositeKey = new EMFExportRefHasValueCompositeKey(matrixName,
					columnHeaderName, refMatrixName, refColumnHeaderName);
			if (refHasValueIndex.containsKey(refHasValueCompositeKey)) {
				return refHasValueIndex.get(refHasValueCompositeKey);
			}

			if (matrixNameToMatrixMap.containsKey(matrixName) && matrixNameToMatrixMap.containsKey(refMatrixName)) {

				Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

				Map<Integer, Object> firstMatrixRow = matrix.row(getMatrixRowKey(1));

				Integer matrixColumnKey = findMatrixColumnKey(columnHeaderName, firstMatrixRow);
				if (matrixColumnKey == null) {
					throw new EMFExportException(String.format("Column named %s does not exist in matrix named %s!",
							columnHeaderName, matrixName));
				}

				Table<Integer, Integer, Object> refMatrix = matrixNameToMatrixMap.get(refMatrixName);

				Map<Integer, Object> firstRefMatrixRow = refMatrix.row(getMatrixRowKey(1));

				Integer refMatrixColumnKey = findMatrixColumnKey(refColumnHeaderName, firstRefMatrixRow);
				if (refMatrixColumnKey == null) {
					throw new EMFExportException(String.format("Column named %s does not exist in matrix named %s!",
							refColumnHeaderName, refMatrixName));
				}

				List<String> refIDs = findRefIDs(matrix, matrixColumnKey);

				if (!refIDs.isEmpty()) {

					boolean refIDsHaveValue = false;

					for (String refID : refIDs) {

						int refMatrixRowKey = findRefMatrixRowKey(refMatrixName, refID);

						if (refMatrixRowKey != -1) {

							Object refMatrixColumnRowValue = refMatrix.get(getMatrixRowKey(refMatrixRowKey),
									refMatrixColumnKey);

							refIDsHaveValue = refHasValue(refIDsHaveValue, refMatrixColumnRowValue);
						}
					}

					refHasValue = refIDsHaveValue;
				}
			}

			refHasValueIndex.put(refHasValueCompositeKey, Boolean.valueOf(refHasValue));
		}

		return refHasValue;
	}

	private boolean refHasValue(boolean refIDsHaveValue, Object refMatrixColumnRowValue) {
		if ((refMatrixColumnRowValue != null)
				&& !(refMatrixColumnRowValue instanceof Optional && ((Optional<?>) refMatrixColumnRowValue).isEmpty())
				&& (((refMatrixColumnRowValue instanceof EMFExportEObjectOneReferenceValueCell)
						&& ((EMFExportEObjectOneReferenceValueCell) refMatrixColumnRowValue).hasValue())
						|| ((refMatrixColumnRowValue instanceof EMFExportEObjectManyReferencesValueCell)
								&& ((EMFExportEObjectManyReferencesValueCell) refMatrixColumnRowValue).hasValues())
						|| ((refMatrixColumnRowValue instanceof EMFExportEObjectIDValueCell)
								&& ((EMFExportEObjectIDValueCell) refMatrixColumnRowValue).hasValue())
						|| (String.valueOf(refMatrixColumnRowValue) != null
								&& !String.valueOf(refMatrixColumnRowValue).isEmpty()))) {
			refIDsHaveValue = true;
		}

		return refIDsHaveValue;
	}

	private List<String> findRefIDs(Table<Integer, Integer, Object> matrix, Integer matrixColumnKey) {
		List<String> refIDs = new ArrayList<>();

		Map<Integer, Object> matrixColumn = matrix.column(matrixColumnKey);
		for (Map.Entry<Integer, Object> matrixColumnRow : matrixColumn.entrySet()) {
			Object matrixColumnRowValue = matrixColumnRow.getValue();

			if (matrixColumnRowValue instanceof EMFExportEObjectColumnHeader) {
				continue;
			}

			if (matrixColumnRowValue instanceof EMFExportEObjectIDValueCell) {
				if (((EMFExportEObjectIDValueCell) matrixColumnRowValue).hasValue()) {
					refIDs.add(((EMFExportEObjectIDValueCell) matrixColumnRowValue).getValue());
				}
			} else if (matrixColumnRowValue instanceof EMFExportEObjectOneReferenceValueCell) {
				if (((EMFExportEObjectOneReferenceValueCell) matrixColumnRowValue).hasValue()) {
					refIDs.add(((EMFExportEObjectOneReferenceValueCell) matrixColumnRowValue).getValue());
				}

			} else if (matrixColumnRowValue instanceof EMFExportEObjectManyReferencesValueCell) {
				if (((EMFExportEObjectManyReferencesValueCell) matrixColumnRowValue).hasValues()) {
					refIDs.addAll(((EMFExportEObjectManyReferencesValueCell) matrixColumnRowValue).getValues());
				}
			}
		}
		return refIDs;
	}

	private Integer findMatrixColumnKey(String columnHeaderName, Map<Integer, Object> matrixRow) {
		Integer matrixColumnKey = null;

		for (Map.Entry<Integer, Object> matrixRowColumn : matrixRow.entrySet()) {
			Object value = matrixRowColumn.getValue();

			if (value instanceof EMFExportEObjectColumnHeader && columnHeaderName
					.equalsIgnoreCase(((EMFExportEObjectColumnHeader) value).getColumnHeaderName())) {
				matrixColumnKey = matrixRowColumn.getKey();
			}
		}

		return matrixColumnKey;
	}

	private boolean columnHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EMFExportEObjectColumnHeader matrixColumnHeader) throws EMFExportException {

		if (matrixColumnHeader == null) {
			throw new EMFExportException(String.format("Matrix column header is required!"));
		}

		return columnHasValue(matrixNameToMatrixMap,
				((EMFExportEObjectColumnHeader) matrixColumnHeader).getMatrixName(),
				((EMFExportEObjectColumnHeader) matrixColumnHeader).getColumnHeaderName());
	}

	private boolean columnHasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			String matrixName, String columnHeaderName) throws EMFExportException {

		boolean columnHasValue = false;

		EMFExportColumnHasValueCompositeKey columnHasValueCompositeKey = new EMFExportColumnHasValueCompositeKey(
				matrixName, columnHeaderName);
		if (columnHasValueIndex.containsKey(columnHasValueCompositeKey)) {
			return columnHasValueIndex.get(columnHasValueCompositeKey);
		}

		if (matrixNameToMatrixMap.containsKey(matrixName)) {

			Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

			Map<Integer, Object> firstMatrixRow = matrix.row(getMatrixRowKey(1));

			Integer matrixColumnKey = findMatrixColumnKey(columnHeaderName, firstMatrixRow);
			if (matrixColumnKey == null) {
				throw new EMFExportException(String.format("Column named %s does not exist in matrix named %s!",
						columnHeaderName, matrixName));
			}

			columnHasValue = columnHasValue(matrix, matrixColumnKey);

			columnHasValueIndex.put(columnHasValueCompositeKey, Boolean.valueOf(columnHasValue));
		}

		return columnHasValue;
	}

	private boolean columnHasValue(Table<Integer, Integer, Object> matrix, Integer matrixColumnKey) {
		boolean columnHasValue = false;

		Map<Integer, Object> matrixColumn = matrix.column(matrixColumnKey);
		for (Map.Entry<Integer, Object> matrixColumnRow : matrixColumn.entrySet()) {
			Object matrixColumnRowValue = matrixColumnRow.getValue();

			if (matrixColumnRowValue instanceof EMFExportEObjectColumnHeader) {
				continue;
			}

			if (columnHasValue(matrixColumnRowValue)) {
				columnHasValue = true;
				break;
			}
		}

		return columnHasValue;
	}

	private boolean columnHasValue(Object matrixColumnRowValue) {
		return ((matrixColumnRowValue != null)
				&& !(matrixColumnRowValue instanceof Optional && ((Optional<?>) matrixColumnRowValue).isEmpty())
				&& (((matrixColumnRowValue instanceof EMFExportEObjectOneReferenceValueCell)
						&& ((EMFExportEObjectOneReferenceValueCell) matrixColumnRowValue).hasValue())
						|| ((matrixColumnRowValue instanceof EMFExportEObjectManyReferencesValueCell)
								&& ((EMFExportEObjectManyReferencesValueCell) matrixColumnRowValue).hasValues())
						|| ((matrixColumnRowValue instanceof EMFExportEObjectIDValueCell)
								&& ((EMFExportEObjectIDValueCell) matrixColumnRowValue).hasValue())
						|| (String.valueOf(matrixColumnRowValue) != null
								&& !String.valueOf(matrixColumnRowValue).isEmpty())));
	}

	private boolean hasValue(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EMFExportEObjectColumnHeader matrixColumnHeader, EMFExportEObjectColumnHeader refMatrixColumnHeader)
			throws EMFExportException {

		if (refMatrixColumnHeader != null && matrixColumnHeader != refMatrixColumnHeader) {
			return refHasValue(matrixNameToMatrixMap, matrixColumnHeader, refMatrixColumnHeader);
		} else {
			return columnHasValue(matrixNameToMatrixMap, matrixColumnHeader);
		}
	}

	private Set<String> nonRefMatrixNames(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) {
		Set<String> refMatrixNames = refMatrixNames(matrixNameToMatrixMap);

		return matrixNameToMatrixMap.keySet().stream().filter(matrixName -> !refMatrixNames.contains(matrixName))
				.collect(Collectors.toSet());
	}

	@SuppressWarnings("unused") // TODO: remove if not needed
	private Set<String> nonRefMatrixNames(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> refMatrixNames) {
		return matrixNameToMatrixMap.keySet().stream().filter(matrixName -> !refMatrixNames.contains(matrixName))
				.collect(Collectors.toSet());
	}

	private Set<String> refMatrixNames(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) {
		Set<String> filterMatrixNames = new HashSet<>();

		for (String matrixName : matrixNameToMatrixMap.keySet()) {
			Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

			Map<Integer, Object> firstRow = matrix.row(getMatrixRowKey(1));

			for (Map.Entry<Integer, Object> rowColumn : firstRow.entrySet()) {
				Object value = rowColumn.getValue();

				if (value instanceof EMFExportEObjectReferenceColumnHeader) {
					String refMatrixName = ((EMFExportEObjectReferenceColumnHeader) value).getRefMatrixName();

					filterMatrixNames.add(refMatrixName);
				}
			}
		}
		return filterMatrixNames;
	}

	private void validateClassHierarchyForRootObjects(List<EObject> eObjects) throws EMFExportException {
		Class<?> clazz = null;
		for (EObject eObject : eObjects) {
			if (clazz == null) {
				clazz = eObject.getClass();
			} else {
				if (clazz.isAssignableFrom(eObject.getClass())) {
					continue;
				} else if (eObject.getClass().isAssignableFrom(clazz)) {
					clazz = eObject.getClass();
				} else {
					throw new EMFExportException(
							String.format("Element type '%s' is not compatible with type of other elements in list!",
									eObject.getClass()));
				}
			}
		}
	}

	@Override
	protected Map<Object, Object> validateExportOptions(Map<?, ?> options) throws EMFExportException {
		Map<Object, Object> exportOptions = super.validateExportOptions(options);

		if (!flatExportMode(exportOptions) && !zipExportMode(exportOptions)) {
			throw new EMFExportException("Please specify export mode!");
		}

		if (flatExportMode(exportOptions)
				&& (exportMetadataEnabled(exportOptions) || addMappingTableEnabled(exportOptions))) {
			throw new EMFExportException(
					"Incompatible combination of export options: neither 'export metadata' nor 'generate mapping table' options can be turned on in flat CSV export mode!");
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

	@Override
	protected boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		if (flatExportMode(exportOptions)) {
			return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.FALSE));
		} else {
			return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.TRUE));
		}
	}

	@Override
	protected boolean addMappingTableEnabled(Map<Object, Object> exportOptions) {
		if (flatExportMode(exportOptions)) {
			return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADD_MAPPING_TABLE, Boolean.FALSE));
		} else {
			return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADD_MAPPING_TABLE, Boolean.TRUE));
		}
	}

	@Override
	protected void resetState() {
		super.resetState();

		this.refsMaxValueCountIndex.clear();
		this.refHasValueIndex.clear();
		this.columnHasValueIndex.clear();
	}
}
