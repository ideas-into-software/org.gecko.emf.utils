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
package org.gecko.emf.exporter.r_lang.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.annotation.ProvideEMFExporter;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectReferenceValueCell;
import org.gecko.emf.exporter.r_lang.api.EMFRLangExportOptions;
import org.gecko.emf.exporter.r_lang.api.EMFRLangExporterConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.primitives.Bytes;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting
 * EMF resources and lists of EMF objects as R Language data-frame.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = EMFRLangExporterConstants.EMF_EXPORTER_NAME, scope = ServiceScope.PROTOTYPE)
@ProvideEMFExporter(name = EMFRLangExporterConstants.EMF_EXPORTER_NAME)
public class EMFRLangExporter extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFRLangExporter.class);

	private static final String RDATA_FILE_EXTENSION = "RData";

	@SuppressWarnings("unused")
	private static final int NILSXP = 0; /* nil = NULL */
	private static final int SYMSXP = 1; /* symbols */
	private static final int LISTSXP = 2; /* lists of dotted pairs */
	@SuppressWarnings("unused")
	private static final int CLOSXP = 3; /* closures */
	@SuppressWarnings("unused")
	private static final int ENVSXP = 4; /* environments */
	@SuppressWarnings("unused")
	private static final int PROMSXP = 5; /* promises: [un]evaluated closure arguments */
	@SuppressWarnings("unused")
	private static final int LANGSXP = 6; /* language constructs (special lists) */
	@SuppressWarnings("unused")
	private static final int SPECIALSXP = 7; /* special forms */
	@SuppressWarnings("unused")
	private static final int BUILTINSXP = 8; /* builtin non-special forms */
	private static final int CHARSXP = 9; /* "scalar" string type (internal only) */
	private static final int LGLSXP = 10; /* logical vectors */
	private static final int INTSXP = 13; /* integer vectors */
	private static final int REALSXP = 14; /* real variables */
	@SuppressWarnings("unused")
	private static final int CPLXSXP = 15; /* complex variables */
	private static final int STRSXP = 16; /* string vectors */
	@SuppressWarnings("unused")
	private static final int DOTSXP = 17; /* dot-dot-dot object */
	@SuppressWarnings("unused")
	private static final int ANYSXP = 18; /*
											 * make "any" args work. Used in specifying types for symbol registration to
											 * mean anything is okay
											 */
	private static final int VECSXP = 19; /* generic vectors */
	@SuppressWarnings("unused")
	private static final int EXPRSXP = 20; /* expressions vectors */
	@SuppressWarnings("unused")
	private static final int BCODESXP = 21; /* byte code */
	@SuppressWarnings("unused")
	private static final int EXTPTRSXP = 22; /* external pointer */
	@SuppressWarnings("unused")
	private static final int RAWSXP = 24; /* raw bytes */
	@SuppressWarnings("unused")
	private static final int S4SXP = 25; /* S4, non-vector */
	@SuppressWarnings("unused")
	private static final int FUNSXP = 99; /* Closure or Builtin or Special */

	private static final int NILVALUESXP = 254;
	@SuppressWarnings("unused")
	private static final int REFSXP = 255;

	@SuppressWarnings("unused")
	private static final int LATIN1_MASK = (1 << 2);
	@SuppressWarnings("unused")
	private static final int UTF8_MASK = (1 << 3);
	private static final int ASCII_MASK = (1 << 6);

	private static final int IS_OBJECT_BIT_MASK = (1 << 8);
	private static final int HAS_ATTR_BIT_MASK = (1 << 9);
	private static final int HAS_TAG_BIT_MASK = (1 << 10);

	private static final int NA_INT = Integer.MIN_VALUE;
	private static final int NA_STRING = -1;

	// This is a special R constant value
	private static final double NA_REAL = ByteBuffer
			.wrap(new byte[] { 0x7f, (byte) 0xf0, 0x00, 0x00, 0x00, 0x00, 0x07, (byte) 0xa2 }).getDouble();

	private static final String OPTION_ENCODE_FLAGS_IS_OBJECT_BIT_MASK = "ENCODE_FLAGS_IS_OBJECT_BIT_MASK";
	private static final String OPTION_ENCODE_FLAGS_HAS_ATTR_BIT_MASK = "ENCODE_FLAGS_HAS_ATTR_BIT_MASK ";
	private static final String OPTION_ENCODE_FLAGS_HAS_TAG_BIT_MASK = "ENCODE_FLAGS_HAS_TAG_BIT_MASK";

	private static final String TYPES_TYPE = "type";
	private static final String TYPES_KEYS = "keys";
	private static final String TYPES_TYPES = "types";

	public EMFRLangExporter() {
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

				LOG.info("Starting export of {} EObject(s) to R language data frames"
						+ (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				LOG.info("  Locale to use: {}", locale(exportOptions));
				LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
				LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
				LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
				LOG.info("  Show URIs instead of IDs (where applicable): {}", showURIsEnabled(exportOptions));
				LOG.info("  Show columns containing references: {}", showREFsEnabled(exportOptions));
				LOG.info("  Dataframe per file: {}", dataframePerFileEnabled(exportOptions));

				ProcessedEObjectsDTO processedEObjectsDTO = exportEObjectsToMatrices(eObjects, exportOptions);

				if (dataframePerFileEnabled(exportOptions)) {
					exportMatricesToRLangInOneDataframePerFileMode(outputStream, processedEObjectsDTO, exportOptions);
				} else {
					exportMatricesToRLangInAllDataframesInOneFileMode(outputStream, processedEObjectsDTO,
							exportOptions);
				}

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToRLangInAllDataframesInOneFileMode(OutputStream outputStream,
			ProcessedEObjectsDTO processedEObjectsDTO, Map<Object, Object> exportOptions) throws EMFExportException {

		resetStopwatch();

		LOG.info("Starting generation of R language data frames in all dataframes in one file mode");

		try {

			writeRDataFileHeader(outputStream);

			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap = Map
					.copyOf(processedEObjectsDTO.matrixNameToMatrixMap);

			Map<String, Multimap<String, Object>> dataFrames = new LinkedHashMap<>();

			List<Multimap<String, Object>> dataFramesMetadata = new ArrayList<>();

			for (String matrixName : matrixNameToMatrixMap.keySet()) {
				LOG.debug("Generating R language data frame for matrix named '{}'", matrixName);

				Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

				ListMultimap<String, Object> dataFrame = constructDataFrame(exportOptions, matrix);
				dataFrames.put(matrixName, dataFrame);

				List<ValueType> dataFrameTypes = extractDataFrameTypes(exportOptions, matrix);

				ListMultimap<String, Object> dataFrameMetadata = constructDataFrameMetadata(dataFrame, dataFrameTypes);
				dataFramesMetadata.add(dataFrameMetadata);
			}

			writeRDataFileData(outputStream, dataFrames, dataFramesMetadata);

			outputStream.close();

		} catch (IOException e) {
			throw new EMFExportException(e);
		}

		LOG.info("Finished generation of R language data frames in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToRLangInOneDataframePerFileMode(OutputStream outputStream,
			ProcessedEObjectsDTO processedEObjectsDTO, Map<Object, Object> exportOptions) throws EMFExportException {

		resetStopwatch();

		LOG.info("Starting generation of R language data frames in one dataframe per file mode");

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

			Map<String, Table<Integer, Integer, Object>> matrixNameToEObjectMatrixMap = eObjectMatricesOnly(
					processedEObjectsDTO.matrixNameToMatrixMap);

			exportMatricesToRLang(zipOutputStream, processedEObjectsDTO, exportOptions, matrixNameToEObjectMatrixMap);

			if (exportMetadataEnabled(exportOptions)) {
				Map<String, Table<Integer, Integer, Object>> matrixNameToMetadataMatrixMap = metadataMatricesOnly(
						processedEObjectsDTO.matrixNameToMatrixMap);

				exportMatricesToRLang(zipOutputStream, processedEObjectsDTO, exportOptions,
						matrixNameToMetadataMatrixMap);
			}

			if (addMappingTableEnabled(exportOptions)) {
				Map<String, Table<Integer, Integer, Object>> matrixNameToMappingMatrixMap = mappingMatricesOnly(
						processedEObjectsDTO.matrixNameToMatrixMap);

				exportMatricesToRLang(zipOutputStream, processedEObjectsDTO, exportOptions,
						matrixNameToMappingMatrixMap);
			}

		} catch (IOException e) {
			throw new EMFExportException(e);
		}

		LOG.info("Finished generation of R language data frames in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatricesToRLang(ZipOutputStream zipOutputStream, ProcessedEObjectsDTO processedEObjectsDTO,
			Map<Object, Object> exportOptions, Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap)
			throws IOException, EMFExportException {

		for (String matrixName : matrixNameToMatrixMap.keySet()) {
			LOG.debug("Generating R language data frame for matrix named '{}'", matrixName);

			Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(matrixName);

			exportMatrixToRLang(exportOptions, matrixName, matrix, zipOutputStream);
		}
	}

	private void exportMatrixToRLang(Map<Object, Object> exportOptions, String matrixName,
			Table<Integer, Integer, Object> matrix, ZipOutputStream zipOutputStream)
			throws IOException, EMFExportException {

		ListMultimap<String, Object> dataFrame = constructDataFrame(exportOptions, matrix);

		List<ValueType> dataFrameTypes = extractDataFrameTypes(exportOptions, matrix);

		ListMultimap<String, Object> dataFrameMetadata = constructDataFrameMetadata(dataFrame, dataFrameTypes);

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

			writeRDataFileHeader(baos);

			writeRDataFileData(baos, Map.of(matrixName, dataFrame), List.of(dataFrameMetadata));

			writeZipEntry(zipOutputStream, matrixName, baos);
		}
	}

	private ListMultimap<String, Object> constructDataFrame(Map<Object, Object> exportOptions,
			Table<Integer, Integer, Object> matrix) {
		ListMultimap<String, Object> dataFrame = MultimapBuilder.linkedHashKeys().arrayListValues().build();

		Set<Integer> matrixColumnKeys = matrix.columnKeySet();

		for (Integer matrixColumnKey : matrixColumnKeys) {
			Map<Integer, Object> matrixColumn = matrix.column(matrixColumnKey);

			String matrixColumnHeader = matrixColumn.get(1).toString();

			List<Object> matrixColumnValues = matrixColumn.values().stream().skip(1)
					.map(v -> convertValue(v, exportOptions)).collect(Collectors.toList());

			dataFrame.putAll(matrixColumnHeader, matrixColumnValues);
		}
		return dataFrame;
	}

	private List<ValueType> extractDataFrameTypes(Map<Object, Object> exportOptions,
			Table<Integer, Integer, Object> matrix) {
		List<ValueType> dataFrameTypes = new ArrayList<>();

		Set<Integer> matrixColumnKeys = matrix.columnKeySet();

		for (Integer matrixColumnKey : matrixColumnKeys) {
			Map<Integer, Object> matrixColumn = matrix.column(matrixColumnKey);

			List<Object> matrixColumnValues = matrixColumn.values().stream().skip(1)
					.map(v -> convertValue(v, exportOptions)).collect(Collectors.toList());

			for (Object matrixColumnValue : matrixColumnValues) {
				if (matrixColumnValue instanceof Integer) {
					dataFrameTypes.add(ValueType.INT);
					break;
				} else if (matrixColumnValue instanceof Double || matrixColumnValue instanceof Float) {
					dataFrameTypes.add(ValueType.REAL);
					break;
				} else if (matrixColumnValue instanceof Boolean) {
					dataFrameTypes.add(ValueType.LOGICAL);
					break;
				} else {
					dataFrameTypes.add(ValueType.STRING);
					break;
				}
			}
		}
		return dataFrameTypes;
	}

	private ListMultimap<String, Object> constructDataFrameMetadata(ListMultimap<String, Object> dataFrame,
			List<ValueType> dataFrameTypes) {
		ListMultimap<String, Object> dataFrameMetadata = MultimapBuilder.linkedHashKeys().arrayListValues().build();
		dataFrameMetadata.put(TYPES_TYPE, ValueType.DATAFRAME);
		dataFrameMetadata.putAll(TYPES_KEYS, dataFrame.keySet());
		dataFrameMetadata.putAll(TYPES_TYPES, dataFrameTypes);
		return dataFrameMetadata;
	}

	private Object convertValue(Object v, Map<Object, Object> exportOptions) {
		if ((v == null) || (v instanceof Optional)
				|| (v instanceof EMFExportEObjectOneReferenceValueCell
						&& !((EMFExportEObjectOneReferenceValueCell) v).hasRefID())
				|| (v instanceof EMFExportEObjectManyReferencesValueCell
						&& !((EMFExportEObjectManyReferencesValueCell) v).hasURIs())) {
			return "";
		} else {
			if (showURIsEnabled(exportOptions) && (v instanceof EMFExportEObjectReferenceValueCell)
					&& !((EMFExportEObjectReferenceValueCell) v).isSelfReferencingModel()) {
				if (v instanceof EMFExportEObjectOneReferenceValueCell
						&& ((EMFExportEObjectOneReferenceValueCell) v).hasURI()) {
					return ((EMFExportEObjectOneReferenceValueCell) v).getURI();
				} else if (v instanceof EMFExportEObjectManyReferencesValueCell
						&& ((EMFExportEObjectManyReferencesValueCell) v).hasURIs()) {
					if (((EMFExportEObjectManyReferencesValueCell) v).getURIsCount() == 1) {
						return ((EMFExportEObjectManyReferencesValueCell) v).getURIs().get(0);
					} else {
						return Arrays.toString(((EMFExportEObjectManyReferencesValueCell) v).getURIs().toArray());
					}
				} else {
					return "";
				}

			} else {
				if ((v instanceof EMFExportEObjectManyReferencesValueCell)
						&& ((EMFExportEObjectManyReferencesValueCell) v).getRefIDsCount() == 1) {
					return ((EMFExportEObjectManyReferencesValueCell) v).getRefIDs().get(0);

				} else if (v instanceof EMFExportEObjectOneReferenceValueCell) {
					if (((EMFExportEObjectOneReferenceValueCell) v).hasRefID()) {
						return ((EMFExportEObjectOneReferenceValueCell) v).getRefID();
					} else {
						return "";
					}
				} else {
					return v;
				}
			}
		}
	}

	private void writeZipEntry(ZipOutputStream zipOutputStream, String matrixName, ByteArrayOutputStream baos)
			throws IOException {
		String zipEntryName = constructZipEntryName(matrixName);
		ZipEntry zipEntry = new ZipEntry(zipEntryName);
		zipOutputStream.putNextEntry(zipEntry);

		try (InputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
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
		sb.append(RDATA_FILE_EXTENSION);
		return sb.toString();
	}

	private <T> void applyForEach(List<T> collection, ForEachApplier<T, Integer> iteratee) throws EMFExportException {
		for (int i = 0; i < collection.size(); i++) {
			iteratee.apply(collection.get(i), Integer.valueOf(i));
		}
	}

	private int packedVersion(int v, int p, int s) {
		return s + (p * 256) + (v * 65536);
	}

	private ByteBuffer encodeInt(int value) {
		ByteBuffer buf = ByteBuffer.allocate(4);
		buf.putInt(value);
		return buf;
	}

	private ByteBuffer encodeReal(double value) {
		ByteBuffer buf = ByteBuffer.allocate(8);
		buf.putDouble(value);
		return buf;
	}

	private int encodeFlags(int base_type, Map<String, Boolean> options) {
		if (options == null) {
			options = new HashMap<>();
		}

		int flags = base_type;

		if (options.getOrDefault(OPTION_ENCODE_FLAGS_IS_OBJECT_BIT_MASK, Boolean.FALSE)) {
			flags |= IS_OBJECT_BIT_MASK;
		}

		if (options.getOrDefault(OPTION_ENCODE_FLAGS_HAS_ATTR_BIT_MASK, Boolean.FALSE)) {
			flags |= HAS_ATTR_BIT_MASK;
		}

		if (options.getOrDefault(OPTION_ENCODE_FLAGS_HAS_TAG_BIT_MASK, Boolean.FALSE)) {
			flags |= HAS_TAG_BIT_MASK;
		}

		return flags;
	}

	private void writeRDataFileHeader(OutputStream stream) {
		write(stream, "RDX2\nX\n".getBytes());
		write(stream, encodeInt(2).array());
		write(stream, encodeInt(packedVersion(3, 0, 0)).array());
		write(stream, encodeInt(packedVersion(2, 3, 0)).array());
	}

	private void writeValue(OutputStream stream, Object[] value, ValueType valueType) throws Exception {

		switch (valueType) {

		case INT:
			intVector(stream, convertToArrayOf(value, new ValueOfConverter<Integer>() {

				@Override
				public Integer apply(Object obj) {
					if (obj == null || String.valueOf(obj).isEmpty()) {
						return Integer.valueOf(-1);
					}

					return Integer.valueOf(String.valueOf(obj));
				}
			}, Integer[]::new));

			break;

		case REAL:
			realVector(stream, convertToArrayOf(value, new ValueOfConverter<Double>() {

				@Override
				public Double apply(Object obj) {
					if (obj == null || String.valueOf(obj).isEmpty()) {
						return Double.valueOf(-1);
					}

					return Double.valueOf(String.valueOf(obj));
				}
			}, Double[]::new));

			break;

		case LOGICAL:
			logicalVector(stream, convertToArrayOf(value, new ValueOfConverter<Boolean>() {

				@Override
				public Boolean apply(Object obj) {
					return Boolean.valueOf(String.valueOf(obj));
				}
			}, Boolean[]::new));

			break;

		case STRING:
			stringVector(stream, convertToArrayOf(value, new ValueOfConverter<String>() {

				@Override
				public String apply(Object obj) {
					if (obj == null) {
						return "";
					}

					return String.valueOf(obj);
				}
			}, String[]::new));

			break;

		default:
			throw new Exception("No valid value type specified!");
		}
	}

	private <T> T[] convertToArrayOf(Object[] values, ValueOfConverter<T> valueOfConverter,
			IntFunction<T[]> arrayTenerator) {
		return Arrays.stream(values).map(value -> valueOfConverter.apply(value)).toArray(arrayTenerator);
	}

	private void writeVector(OutputStream stream, Object[] vector, ScalarWriter<Object> method)
			throws EMFExportException {

		applyForEach(List.of(vector), new ForEachApplier<Object, Integer>() {

			@Override
			public void apply(Object element, Integer index) {
				write(stream, method.apply(element));
			}
		});
	}

	private byte[] stringScalar(String string) {
		ByteBuffer type = encodeInt(CHARSXP | (ASCII_MASK << 12));

		if (string == null) {
			type = encodeInt(CHARSXP);
			return Bytes.concat(type.array(), encodeInt(NA_STRING).array());
		}

		byte[] stringByteArr = string.getBytes();

		byte[] stringByteArrLength = encodeInt(stringByteArr.length).array();

		return Bytes.concat(type.array(), stringByteArrLength, stringByteArr);
	}

	private byte[] realScalar(Double realScalar) {
		if (realScalar == null) {
			return encodeReal(NA_REAL).array();
		}

		return encodeReal(realScalar).array();
	}

	private byte[] intScalar(Integer intScalar) {
		if (intScalar == null) {
			return encodeInt(NA_INT).array();
		}

		return encodeInt(intScalar).array();
	}

	private byte[] logicalScalar(Boolean boolScalar) {
		if (boolScalar == null) {
			return encodeInt(NA_INT).array();
		}

		return encodeInt(boolScalar.booleanValue() ? 1 : 0).array();
	}

	private void stringVector(OutputStream stream, String[] vector) throws EMFExportException {

		write(stream, encodeInt(encodeFlags(STRSXP, null)).array());

		write(stream, encodeInt(vector.length).array());

		writeVector(stream, vector, new ScalarWriter<Object>() {

			@Override
			public byte[] apply(Object t) {
				return stringScalar(String.valueOf(t));
			}
		});
	}

	private void realVector(OutputStream stream, Double[] vector) throws EMFExportException {

		write(stream, encodeInt(encodeFlags(REALSXP, null)).array());

		write(stream, encodeInt(vector.length).array());

		writeVector(stream, vector, new ScalarWriter<Object>() {

			@Override
			public byte[] apply(Object t) {
				if (!(t instanceof Double)) {
					throw new IllegalArgumentException("Expecting object of type Double, not + " + t.getClass());
				}
				return realScalar((Double) t);
			}
		});
	}

	private void intVector(OutputStream stream, Integer[] vector) throws EMFExportException {

		write(stream, encodeInt(encodeFlags(INTSXP, null)).array());

		write(stream, encodeInt(vector.length).array());

		writeVector(stream, vector, new ScalarWriter<Object>() {

			@Override
			public byte[] apply(Object t) {
				if (!(t instanceof Integer)) {
					throw new IllegalArgumentException("Expecting object of type Integer, not + " + t.getClass());
				}

				return intScalar((Integer) t);
			}
		});
	}

	private void logicalVector(OutputStream stream, Boolean[] vector) throws EMFExportException {

		write(stream, encodeInt(encodeFlags(LGLSXP, null)).array());

		write(stream, encodeInt(vector.length).array());

		writeVector(stream, vector, new ScalarWriter<Object>() {

			@Override
			public byte[] apply(Object t) {
				if (!(t instanceof Boolean)) {
					throw new IllegalArgumentException("Expecting object of type Boolean, not + " + t.getClass());
				}

				return logicalScalar((Boolean) t);
			}
		});
	}

	private void symbol(OutputStream stream, String string) {
		write(stream, encodeInt(encodeFlags(SYMSXP, null)).array());
		write(stream, stringScalar(string));
	}

	private void writeRDataFileData(OutputStream stream, Map<String, Multimap<String, Object>> pairsMap,
			List<Multimap<String, Object>> typesList) throws EMFExportException {

		List<String> keysList = List.copyOf(pairsMap.keySet());

		applyForEach(keysList, new ForEachApplier<String, Integer>() {

			@Override
			public void apply(String key, Integer idx) throws EMFExportException {

				try {

					write(stream,
							encodeInt(encodeFlags(LISTSXP, Map.of(OPTION_ENCODE_FLAGS_HAS_TAG_BIT_MASK, Boolean.TRUE)))
									.array());

					symbol(stream, key);

					Multimap<String, Object> typesMap = typesList.get(idx.intValue());

					ValueType valueType = ValueType.valueOf(Iterables.get(typesMap.get(TYPES_TYPE), 0).toString());

					if (ValueType.DATAFRAME != valueType) {
						throw new IllegalArgumentException("Expecting DATAFRAME value type!");
					}

					List<String> typesKeys = typesMap.get(TYPES_KEYS).stream().map(typesKey -> ((String) typesKey))
							.collect(Collectors.toList());

					List<ValueType> typesTypes = typesMap.get(TYPES_TYPES).stream()
							.map(typesType -> ValueType.valueOf(typesType.toString())).collect(Collectors.toList());

					writeRDataFileData(stream, pairsMap.get(key), typesKeys, typesTypes);

				} catch (Exception e) {
					throw new EMFExportException(e);
				}

			}
		});

		write(stream, encodeInt(NILVALUESXP).array());
	}

	private void writeRDataFileData(OutputStream stream, Multimap<String, Object> value, List<String> keys,
			List<ValueType> types) throws EMFExportException {

		write(stream, encodeInt(encodeFlags(VECSXP, Map.of(OPTION_ENCODE_FLAGS_IS_OBJECT_BIT_MASK, Boolean.TRUE,
				OPTION_ENCODE_FLAGS_HAS_ATTR_BIT_MASK, Boolean.TRUE))).array());

		write(stream, encodeInt(keys.size()).array());

		int length = value.get(keys.get(0)).size();

		LOG.debug("Writing data frame of length " + length);

		applyForEach(keys, new ForEachApplier<String, Integer>() {

			@Override
			public void apply(String key, Integer idx) throws EMFExportException {

				try {
					writeValue(stream, value.get(key).stream().toArray(Object[]::new), types.get(idx.intValue()));
				} catch (Exception e) {
					throw new EMFExportException(e);
				}
			}
		});

		writeRDataFileMetadata(stream, keys, length);
	}

	private void writeRDataFileMetadata(OutputStream stream, List<String> keys, int length) throws EMFExportException {
		ListMultimap<String, Object> dataFrameMetadata = MultimapBuilder.linkedHashKeys().arrayListValues().build();
		dataFrameMetadata.putAll("names", keys);
		dataFrameMetadata.putAll("class", List.of("data.frame"));
		dataFrameMetadata.putAll("row.names", List.of(NA_INT, (-1 * length)));

		List<String> dataFrameMetadataColumnNames = List.of("names", "row.names", "class");

		List<ValueType> dataFrameMetadataColumnTypes = List.of(ValueType.STRING, ValueType.INT, ValueType.STRING);

		// TODO: attributes - if needed ?

		applyForEach(dataFrameMetadataColumnNames, new ForEachApplier<String, Integer>() {

			@Override
			public void apply(String attributeName, Integer idx) throws EMFExportException {

				try {

					write(stream,
							encodeInt(encodeFlags(LISTSXP, Map.of(OPTION_ENCODE_FLAGS_HAS_TAG_BIT_MASK, Boolean.TRUE)))
									.array());

					symbol(stream, attributeName);

					writeValue(stream, dataFrameMetadata.get(attributeName).stream().toArray(Object[]::new),
							dataFrameMetadataColumnTypes.get(idx.intValue()));

				} catch (Exception e) {
					throw new EMFExportException(e);
				}
			}
		});

		write(stream, encodeInt(NILVALUESXP).array());
	}

	private void write(OutputStream stream, byte[] buffer) {
		try {
			stream.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean dataframePerFileEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFRLangExportOptions.OPTION_DATAFRAME_PER_FILE, Boolean.TRUE));
	}

	private enum ValueType {
		STRING, INT, REAL, LOGICAL, DATAFRAME;
	}

	@FunctionalInterface
	private interface ForEachApplier<T, U> {
		public void apply(T t, U u) throws EMFExportException;
	}

	@FunctionalInterface
	private interface ScalarWriter<T> {
		public byte[] apply(T t);
	}

	@FunctionalInterface
	private interface ValueOfConverter<T> {
		public T apply(Object obj);
	}
}
