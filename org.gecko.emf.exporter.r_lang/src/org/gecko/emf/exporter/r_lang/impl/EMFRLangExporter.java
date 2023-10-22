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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.ecore.EObject;
import org.gecko.emf.exporter.AbstractEMFExporter;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectReferenceValueCell;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.renjin.aether.AetherPackageLoader;
import org.renjin.aether.ConsoleRepositoryListener;
import org.renjin.aether.ConsoleTransferListener;
import org.renjin.eval.Context;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.serialization.RDataWriter;
import org.renjin.serialization.Serialization.SerializationType;
import org.renjin.sexp.SEXP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Table;

/**
 * Implementation of the {@link EMFExporter} to provide support for exporting
 * EMF resources and lists of EMF objects as R Language data-frame.
 * 
 * @author Michal H. Siemaszko
 */
@Component(name = "EMFRLangExporter", scope = ServiceScope.PROTOTYPE)
public class EMFRLangExporter extends AbstractEMFExporter implements EMFExporter {
	private static final Logger LOG = LoggerFactory.getLogger(EMFRLangExporter.class);

	private static final String RDATA_FILE_EXTENSION = "RData";

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

				// TODO: verify consistency of naming (i.e. "R language data frames")
				LOG.info("Starting export of {} EObject(s) to R language data frames"
						+ (!exportOptions.isEmpty() ? " with options" : ""), eObjects.size());
				if (!exportOptions.isEmpty()) {
					LOG.info("  Locale to use: {}", locale(exportOptions));
					LOG.info("  Export non-containment references: {}", exportNonContainmentEnabled(exportOptions));
					LOG.info("  Export metadata: {}", exportMetadataEnabled(exportOptions));
					LOG.info("  Add mapping table: {}", addMappingTableEnabled(exportOptions));
					LOG.info("  Show URIs instead of IDs (where applicable): {}", showURIs(exportOptions));
				}

				Map<String, com.google.common.collect.Table<Integer, Integer, Object>> matrixNameToMatrixMap = exportEObjectsToMatrices(
						eObjects, exportOptions);

				exportMatricesToRLang(outputStream, matrixNameToMatrixMap, exportOptions);

			} catch (Exception e) {
				throw new EMFExportException(e);
			}
		}
	}

	private void exportMatricesToRLang(OutputStream outputStream,
			Map<String, com.google.common.collect.Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Map<Object, Object> exportOptions) throws EMFExportException {

		resetStopwatch();

		LOG.info("Starting generation of R language data frames");

		RenjinScriptEngine renjinScriptEngine = initializeRenjinScriptEngine();

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
			for (String matrixName : matrixNameToMatrixMap.keySet()) {
				LOG.debug("Generating R language data frame for matrix named '{}'", matrixName);

				com.google.common.collect.Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap
						.get(matrixName);

				exportMatrixToRLang(exportOptions, matrixName, matrix, renjinScriptEngine, zipOutputStream);
			}
		} catch (IOException e) {
			throw new EMFExportException(e);
		}

		LOG.info("Finished generation of R language data frames in {} second(s)", elapsedTimeInSeconds());
	}

	private void exportMatrixToRLang(Map<Object, Object> exportOptions, String matrixName,
			com.google.common.collect.Table<Integer, Integer, Object> matrix, RenjinScriptEngine renjinScriptEngine,
			ZipOutputStream zipOutputStream) throws IOException {

		boolean hasTypeLevelMetadataDocumentation = hasTypeLevelMetadataDocumentation(matrixName,
				matrix.row(getMatrixRowKey(1)));

		se.alipsa.renjin.client.datautils.Table rLangMatrix = new se.alipsa.renjin.client.datautils.Table(
				headerList(matrix, hasTypeLevelMetadataDocumentation),
				rowsList(exportOptions, matrix, hasTypeLevelMetadataDocumentation));

		renjinScriptEngine.put("df", rLangMatrix.asDataframe());

		Context topLevelContext = renjinScriptEngine.getTopLevelContext();

		SEXP dfVariable = topLevelContext.getEnvironment().getVariable(topLevelContext, "df");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (RDataWriter writer = new RDataWriter(topLevelContext, baos, SerializationType.ASCII)) {
			writer.save(dfVariable);
		}

		writeZipEntry(zipOutputStream, matrixName, baos);
	}

	private List<String> headerList(Table<Integer, Integer, Object> matrix, boolean hasTypeLevelMetadataDocumentation) {
		// @formatter:off
		return matrix.row(getMatrixRowKey(hasTypeLevelMetadataDocumentation ? 2 : 1)).values()
				.stream()
				.map(v -> String.valueOf(v))
				.collect(Collectors.toList());
		// @formatter:on
	}

	private List<List<Object>> rowsList(Map<Object, Object> exportOptions, Table<Integer, Integer, Object> matrix,
			boolean hasTypeLevelMetadataDocumentation) {
		// @formatter:off
		return matrix.rowMap().entrySet()
			.stream()
			.skip(hasTypeLevelMetadataDocumentation ? 2 : 1)
			.map(entry -> convertValues(exportOptions, entry.getValue().values()))
			.collect(Collectors.toList());
		// @formatter:on
	}

	private List<Object> convertValues(Map<Object, Object> exportOptions, Collection<Object> rawRowValues) {
		// @formatter:off
		return rawRowValues
				.stream()
				.map(v -> convertValue(v, exportOptions) )
				.collect(Collectors.toList());
		// @formatter:on
	}

	private Object convertValue(Object v, Map<Object, Object> exportOptions) {
		if ((v == null) || (v instanceof Optional)) {
			return Optional.empty();
		} else {
			if (showURIs(exportOptions) && (v instanceof EMFExportEObjectReferenceValueCell)) {
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
					return Optional.empty();
				}

			} else if (!showURIs(exportOptions) && (v instanceof EMFExportEObjectReferenceValueCell)) {
				if (v instanceof EMFExportEObjectOneReferenceValueCell
						&& ((EMFExportEObjectOneReferenceValueCell) v).hasRefID()) {
					return ((EMFExportEObjectOneReferenceValueCell) v).getRefID();
				} else if (v instanceof EMFExportEObjectManyReferencesValueCell
						&& ((EMFExportEObjectManyReferencesValueCell) v).hasRefIDs()) {
					if (((EMFExportEObjectManyReferencesValueCell) v).getRefIDsCount() == 1) {
						return ((EMFExportEObjectManyReferencesValueCell) v).getRefIDs().get(0);
					} else {
						return Arrays.toString(((EMFExportEObjectManyReferencesValueCell) v).getRefIDs().toArray());
					}
				} else {
					return Optional.empty();
				}
			}
		}

		// TODO: use '*ArrayVector' implementation depending on column type ?
		return v;
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

	private boolean hasTypeLevelMetadataDocumentation(String matrixName, Map<Integer, Object> firstRow) {
		boolean isMetadataSheet = (matrixName.contains(METADATA_MATRIX_NAME_SUFFIX));
		boolean isTypeLevelMetadataDocumentationPresent = (firstRow.size() == 2)
				&& firstRow.containsKey(getMatrixColumnKey(0))
				&& String.valueOf(firstRow.get(getMatrixColumnKey(0))).equalsIgnoreCase(METADATA_DOCUMENTATION_HEADER);

		return isMetadataSheet && isTypeLevelMetadataDocumentationPresent;
	}

	private RenjinScriptEngine initializeRenjinScriptEngine() {
		AetherPackageLoader aetherPackageLoader = new AetherPackageLoader(AetherPackageLoader.class.getClassLoader());
		aetherPackageLoader.setTransferListener(new ConsoleTransferListener());
		aetherPackageLoader.setRepositoryListener(new ConsoleRepositoryListener(System.out));

		ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		SessionBuilder sessionBuilder = new SessionBuilder();
		sessionBuilder.withDefaultPackages();
		sessionBuilder.setClassLoader(aetherPackageLoader.getClassLoader());
		sessionBuilder.setPackageLoader(aetherPackageLoader);
		sessionBuilder.setExecutorService(threadPool);

		Session session = sessionBuilder.build();

		return new RenjinScriptEngineFactory().getScriptEngine(session);
	}
}
