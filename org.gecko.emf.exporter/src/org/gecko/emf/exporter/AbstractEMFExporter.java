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
package org.gecko.emf.exporter;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.gecko.emf.exporter.cells.EMFExportEObjectIDValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell;
import org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell;
import org.gecko.emf.exporter.cells.EMFExportMappingMatrixReferenceValueCell;
import org.gecko.emf.exporter.headers.EMFExportEObjectColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectGenericColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectIDColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectManyReferencesColumnHeader;
import org.gecko.emf.exporter.headers.EMFExportEObjectOneReferenceColumnHeader;
import org.gecko.emf.exporter.keys.EMFExportRefMatrixNameIDCompositeKey;
import org.slf4j.Logger;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Implementation of format-agnostic methods for exporting EMF resources and
 * lists of EMF objects to desired format.
 * 
 * Format-specific implementations should extend this class and only focus on
 * transformation of matrices resulting from {@link #exportEObjectsToMatrices}
 * to arrive at desired format.
 * 
 * @author Michal H. Siemaszko
 */
public abstract class AbstractEMFExporter implements EMFExporter {
	protected static final List<String> METADATA_ECLASS_MATRIX_COLUMNS_HEADERS = List.of("Name", "Type", "isMany",
			"isRequired", "Default value", "Documentation");

	protected static final List<String> METADATA_EENUM_MATRIX_COLUMNS_HEADERS = List.of("Name", "Literal", "Value",
			"Documentation");

	protected static final String METADATA_DOCUMENTATION_HEADER = "Documentation";
	protected static final String METADATA_PSEUDOID_DOCUMENTATION = "No default ID present, therefore pseudo-ID was generated for identification purposes";

	protected static final String METADATA_MATRIX_NAME_SUFFIX = "Metadata";
	protected static final String MAPPING_MATRIX_NAME_SUFFIX = "Mapping";

	protected static final String DOCUMENTATION_GENMODEL_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
	protected static final String DOCUMENTATION_GENMODEL_DETAILS = "documentation";

	protected static final String ECORE_PACKAGE_NAME = "ecore";

	protected static final String ID_COLUMN_NAME = "Id";

	protected static final String ID_ATTRIBUTE_NAME = "id";

	protected static final String REF_COLUMN_PREFIX = "ref_";

	protected static final int MAX_COLUMNS = 16384;

	protected static final int MAX_ROWS = 1048576;

	private final Logger logger;

	protected final Stopwatch stopwatch;

	// maps EObjects' IDs to names of matrices, so those can be looked up e.g. when
	// constructing links
	protected final Map<String, String> eObjectIDToMatrixNameMap;

	// maps EObjects' unique identifiers to pseudo IDs - for those EObjects which
	// lack id field
	protected final Map<String, String> eObjectUniqueIdentifierToPseudoIDMap;

	// stores EObjects' EClasses for which pseudo IDs where generated
	protected final Set<EClass> eObjectsClassesWithPseudoIDs;

	// stores EObjects' EClasses - used e.g. to construct meta data
	protected final Set<EClass> eObjectsClasses;

	// stores EEnums - used e.g. to construct meta data
	protected final Set<EEnum> eObjectsEnums;

	// lookup index for storing information about position (row number) of specific
	// reference
	protected final Map<EMFExportRefMatrixNameIDCompositeKey, Integer> refMatrixRowKeyIndex;

	protected AbstractEMFExporter(Logger logger, Stopwatch stopwatch) {
		this.logger = logger;
		this.stopwatch = stopwatch;

		this.eObjectIDToMatrixNameMap = new HashMap<>();
		this.eObjectUniqueIdentifierToPseudoIDMap = new HashMap<>();
		this.eObjectsClassesWithPseudoIDs = new HashSet<>();
		this.eObjectsClasses = new HashSet<>();
		this.eObjectsEnums = new HashSet<>();
		this.refMatrixRowKeyIndex = new HashMap<>();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExporter#exportResourceTo(org.eclipse.emf.ecore.resource.Resource, java.io.OutputStream, java.util.Map)
	 */
	@Override
	public void exportResourceTo(Resource resource, OutputStream outputStream, Map<?, ?> options)
			throws EMFExportException {
		Objects.requireNonNull(resource, "Resource is required for export!");

		try {

			exportEObjectsTo(resource.getContents(), outputStream, options);

		} catch (Exception e) {
			throw new EMFExportException(e);
		}
	}

	/**
	 * Exports list of EObject(s) to matrices, which can further be transformed to
	 * arrive at desired format.
	 * 
	 * @param eObjects
	 * @param options
	 * @return map of matrices, where key is matrix name and value matrix itself.
	 * @throws EMFExportException
	 */
	protected Map<String, Table<Integer, Integer, Object>> exportEObjectsToMatrices(List<? extends EObject> eObjects,
			Map<?, ?> options) throws EMFExportException {
		Objects.requireNonNull(eObjects, "At least one EObject is required for export!");

		resetState();

		final Map<Object, Object> exportOptions = validateExportOptions(options);

		// pseudo IDs are needed before main processing starts
		generatePseudoIDs(eObjects);

		// @formatter:off
		Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap = constructMatrices( 
				eObjects,
				exportOptions);
		// @formatter:on

		validateMatricesColumnsSize(matrixNameToMatrixMap);

		validateMatricesRowsSize(matrixNameToMatrixMap);

		// @formatter:off
		populateMatricesWithData(
				matrixNameToMatrixMap,
				eObjects,
				exportOptions);
		// @formatter:on

		if (exportMetadataEnabled(exportOptions)) {
			// @formatter:off
			populateMatricesWithMetadata(
					matrixNameToMatrixMap);
			// @formatter:on
		}

		return matrixNameToMatrixMap;
	}

	private Map<String, Table<Integer, Integer, Object>> constructMatrices(List<? extends EObject> eObjects,
			Map<Object, Object> exportOptions) throws EMFExportException {

		resetStopwatch();

		logger.info("Starting construction of matrices");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap = new HashMap<String, Table<Integer, Integer, Object>>();

		for (EObject eObject : eObjects) {

			// @formatter:off
			constructMatrixForEObjectWithEReferences(
					matrixNameToMatrixMap,
					processedEObjectsIdentifiers, 
					exportOptions,
					eObject);
			// @formatter:on
		}

		logger.info("Finished construction of matrices in {} second(s)", elapsedTimeInSeconds());

		return matrixNameToMatrixMap;
	}

	private void constructMatrixForEObjectWithEReferences(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, Set<String> eObjectsIdentifiers,
			Map<Object, Object> exportOptions, EObject eObject) throws EMFExportException {

		// @formatter:off
		constructMatrix(
				matrixNameToMatrixMap, 
				eObjectsIdentifiers, 
				exportOptions,
				eObject);
		// @formatter:on

		for (EReference eReference : eObject.eClass().getEAllReferences()) {

			// @formatter:off
			constructMatrixForEReference(
					matrixNameToMatrixMap, 
					eObjectsIdentifiers, 
					exportOptions, 
					eObject, 
					eReference);
			// @formatter:on
		}
	}

	private void constructMatrix(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> eObjectsIdentifiers, Map<Object, Object> exportOptions, EObject... eObjects)
			throws EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			String matrixName = constructEClassMatrixName(eClass);

			constructMatrixIfNotExists(matrixNameToMatrixMap, matrixName, eClass, eObjects[0], hasPseudoID(eObjects[0]),
					exportOptions);

			for (EObject eObject : eObjects) {
				String eObjectIdentifier = getEObjectIdentifier(eObject);

				eObjectsIdentifiers.add(eObjectIdentifier);
				this.eObjectsClasses.add(eObject.eClass());

				if (hasID(eObject)) {
					this.eObjectIDToMatrixNameMap.put(getID(eObject), matrixName);
				} else if (hasPseudoID(eObject)) {
					this.eObjectIDToMatrixNameMap.put(getPseudoID(eObject), matrixName);
				}

				for (EReference eReference : eObject.eClass().getEAllReferences()) {

					// @formatter:off
					constructMatrixForEReference(
							matrixNameToMatrixMap,
							eObjectsIdentifiers, 
							exportOptions, 
							eObject, 
							eReference);
					// @formatter:on
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void constructMatrixForEReference(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> eObjectsIdentifiers, Map<Object, Object> exportOptions, EObject eObject, EReference r)
			throws EMFExportException {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {

			if (!r.isMany()) {

				// @formatter:off
				constructMatrix(
						matrixNameToMatrixMap,
						eObjectsIdentifiers,
						exportOptions,
						(EObject) value);
				// @formatter:on

			} else if (r.isMany()) {

				// @formatter:off
				constructMatrix(
						matrixNameToMatrixMap,
						eObjectsIdentifiers,
						exportOptions,
						((List<EObject>) value).toArray(EObject[]::new));
				// @formatter:on

				if (addMappingTableEnabled(exportOptions)
						&& (!((List<EObject>) value).isEmpty() && (((List<EObject>) value).size() > 1))) {

					// @formatter:off
					constructEReferencesMappingMatrixIfNotExists(
							matrixNameToMatrixMap,
							eObject,
							r);
					// @formatter:on
				}
			}
		}
	}

	private void constructEReferencesMappingMatrixIfNotExists(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, EObject fromEObject,
			EReference toEReference) {
		String eReferencesMappingMatrixName = constructEReferencesMappingMatrixName(fromEObject.eClass(),
				toEReference.getName());

		if (!matrixNameToMatrixMap.containsKey(eReferencesMappingMatrixName)) {
			logger.debug("Creating EReferences mapping matrix named '{}'", eReferencesMappingMatrixName);

			Table<Integer, Integer, Object> eReferencesMappingMatrix = HashBasedTable.create();

			// @formatter:off
			constructEReferencesMappingMatrixColumnHeaders(
					eReferencesMappingMatrix, 
					fromEObject.eClass(), 
					toEReference.getEReferenceType());
			// @formatter:on

			matrixNameToMatrixMap.put(eReferencesMappingMatrixName, eReferencesMappingMatrix);
		}
	}

	private void constructEReferencesMappingMatrixColumnHeaders(
			Table<Integer, Integer, Object> eReferencesMappingMatrix, EClass fromEClass, EClass toEClass) {

		String fromEClassColumnHeaderName = constructEReferencesMappingMatrixColumnHeaderName(fromEClass);
		eReferencesMappingMatrix.put(getMatrixRowKey(1), getMatrixColumnKey(0), fromEClassColumnHeaderName);

		String toEClassColumnHeaderName = constructEReferencesMappingMatrixColumnHeaderName(toEClass);
		eReferencesMappingMatrix.put(getMatrixRowKey(1), getMatrixColumnKey(1), toEClassColumnHeaderName);
	}

	private String constructEReferencesMappingMatrixColumnHeaderName(EClass eClass) {
		StringBuilder sb = new StringBuilder(100);

		EAttribute idAttribute = eClass.getEIDAttribute();

		if (idAttribute == null || idAttribute.getName().equalsIgnoreCase(ID_COLUMN_NAME)) {
			sb.append(eClass.getName().toLowerCase());
			sb.append(ID_COLUMN_NAME);
		} else {
			sb.append(idAttribute.getName());
		}

		return sb.toString();
	}

	protected String constructEReferencesMappingMatrixName(EClass fromEClass, String fromFieldName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(fromEClass.getName());
		sb.append("_");
		sb.append(fromFieldName);
		sb.append(" ");
		sb.append("(");
		sb.append(MAPPING_MATRIX_NAME_SUFFIX);
		sb.append(")");
		return createSafeMatrixName(sb.toString());
	}

	private Table<Integer, Integer, Object> constructMatrixIfNotExists(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, String matrixName, EClass eClass,
			EObject eObject, boolean hasPseudoID, Map<Object, Object> exportOptions) throws EMFExportException {

		if (matrixNameToMatrixMap.containsKey(matrixName)) {
			logger.debug("Matrix named '{}' already exists!", matrixName);

			return matrixNameToMatrixMap.get(matrixName);

		} else {
			logger.debug("Matrix named '{}' does not exist yet, creating...", matrixName);

			// @formatter:off
			Table<Integer, Integer, Object> matrix = constructMatrix(
					matrixNameToMatrixMap,
					matrixName, 
					eClass,
					eObject,
					hasPseudoID, 
					exportOptions);
			// @formatter:on

			matrixNameToMatrixMap.put(matrixName, matrix);

			if (exportMetadataEnabled(exportOptions)) {
				constructMetadataMatrixIfNotExists(matrixNameToMatrixMap, eClass);
			}

			return matrix;
		}
	}

	private Table<Integer, Integer, Object> constructMatrix(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, String matrixName, EClass eClass,
			EObject eObject, boolean hasPseudoID, Map<Object, Object> exportOptions) throws EMFExportException {

		Table<Integer, Integer, Object> matrix = HashBasedTable.create();

		// @formatter:off
		constructMatrixColumnHeadersAndMetadataMatrixIfEnabled(
				matrixNameToMatrixMap,
				matrixName,
				matrix, 
				eClass, 
				eObject,
				hasPseudoID,
				exportOptions);
		// @formatter:on

		return matrix;
	}

	private void constructMatrixColumnHeadersAndMetadataMatrixIfEnabled(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, String matrixName,
			Table<Integer, Integer, Object> matrix, EClass eClass, EObject eObject, boolean hasPseudoID,
			Map<Object, Object> exportOptions) throws EMFExportException {

		logger.debug("Creating columns' headers for matrix named '{}'"
				+ (hasPseudoID ? " with pseudo ID column" : " without pseudo ID column"), matrixName);

		List<EStructuralFeature> eAllStructuralFeatures = eClass.getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		logger.debug("Matrix named '{}' has {} column(s) based on number of structure features", matrixName,
				columnsCount);

		boolean hasIDOrPseudoID = hasPseudoID || hasID(eObject);

		if (hasIDOrPseudoID) {
			constructMatrixIDColumnHeader(matrix, matrixName);
		}

		Iterator<EStructuralFeature> eAllStructuralFeaturesIt = eAllStructuralFeatures.iterator();

		int colIndex = 0;

		while (eAllStructuralFeaturesIt.hasNext()) {
			EStructuralFeature eStructuralFeature = eAllStructuralFeaturesIt.next();

			if (skipFeature(eObject, eStructuralFeature)) {
				continue;
			}

			if (isEcoreEEnumDataType(eStructuralFeature)) {
				EEnum eEnum = extractEEnumDataType(eStructuralFeature);

				this.eObjectsEnums.add(eEnum);

				if (exportMetadataEnabled(exportOptions)) {
					constructMetadataMatrixIfNotExists(matrixNameToMatrixMap, eEnum);
				}
			}

			constructMatrixColumnHeader(eObject, eStructuralFeature, matrixName, matrix,
					(hasIDOrPseudoID ? (colIndex + 1) : colIndex));

			colIndex++;
		}
	}

	private void constructMatrixColumnHeader(EObject eObject, EStructuralFeature eStructuralFeature, String matrixName,
			Table<Integer, Integer, Object> matrix, int colIndex) throws EMFExportException {
		String columnHeaderName = constructMatrixColumnHeaderName(eStructuralFeature);

		if (eStructuralFeature instanceof EAttribute) {
			constructMatrixGenericColumnHeader(matrix, matrixName, columnHeaderName, colIndex);

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			String refMatrixName = constructEClassMatrixName(eReference.getEReferenceType());

			if (!eReference.isMany()) {
				constructMatrixOneReferenceColumnHeader(matrix, matrixName, refMatrixName, columnHeaderName, colIndex);

			} else if (eReference.isMany()) {
				constructMatrixManyReferencesColumnHeader(matrix, matrixName, refMatrixName, columnHeaderName,
						colIndex);
			}

		} else {
			throw new EMFExportException("Only 'EAttribute' and 'EReference' structural features are supported!");
		}
	}

	private void constructMatrixIDColumnHeader(Table<Integer, Integer, Object> matrix, String matrixName) {
		constructMatrixColumnHeader(matrix, new EMFExportEObjectIDColumnHeader(matrixName, ID_COLUMN_NAME), 0);
	}

	private void constructMatrixOneReferenceColumnHeader(Table<Integer, Integer, Object> matrix, String matrixName,
			String refMatrixName, String columnHeaderName, int colIndex) {
		constructMatrixColumnHeader(matrix,
				new EMFExportEObjectOneReferenceColumnHeader(matrixName, refMatrixName, columnHeaderName), colIndex);
	}

	private void constructMatrixManyReferencesColumnHeader(Table<Integer, Integer, Object> matrix, String matrixName,
			String refMatrixName, String columnHeaderName, int colIndex) {
		constructMatrixColumnHeader(matrix,
				new EMFExportEObjectManyReferencesColumnHeader(matrixName, refMatrixName, columnHeaderName), colIndex);
	}

	private void constructMatrixGenericColumnHeader(Table<Integer, Integer, Object> matrix, String matrixName,
			String columnHeaderName, int colIndex) {
		constructMatrixColumnHeader(matrix, new EMFExportEObjectGenericColumnHeader(matrixName, columnHeaderName),
				colIndex);
	}

	private void constructMatrixColumnHeader(Table<Integer, Integer, Object> matrix,
			EMFExportEObjectColumnHeader columnHeader, int colIndex) {
		matrix.put(getMatrixRowKey(1), getMatrixColumnKey(colIndex), columnHeader);
	}

	private void populateMatricesWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			List<? extends EObject> eObjects, Map<Object, Object> exportOptions) throws EMFExportException {

		resetStopwatch();

		logger.info("Starting populating matrices with data");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			populateMatrixWithDataForEObjectWithEReferences(
					matrixNameToMatrixMap, 
					processedEObjectsIdentifiers, 
					exportOptions,
					eObject);
			// @formatter:on
		}

		logger.info("Finished populating matrices with data in {} second(s)", elapsedTimeInSeconds());
	}

	private void populateMatrixWithDataForEObjectWithEReferences(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, Set<String> eObjectsIdentifiers,
			Map<Object, Object> exportOptions, EObject eObject) throws EMFExportException {

		// @formatter:off
		populateMatrixWithData(
				matrixNameToMatrixMap,
				eObjectsIdentifiers,
				exportOptions,
				eObject);
		// @formatter:on

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			try {

				// @formatter:off
				populateMatrixWithDataForEReference(
						matrixNameToMatrixMap,
						eObjectsIdentifiers,
						exportOptions,
						eObject,
						r);
				// @formatter:on

			} catch (EMFExportException e) {
				e.printStackTrace();
			}
		});
	}

	private void populateMatrixWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> eObjectsIdentifiers, Map<Object, Object> exportOptions, EObject... eObjects)
			throws EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Table<Integer, Integer, Object> matrix = getMatrix(matrixNameToMatrixMap, eClass);

			for (EObject eObject : eObjects) {
				eObjectsIdentifiers.add(getEObjectIdentifier(eObject));

				// @formatter:off
				populateMatrixWithData(
						matrixNameToMatrixMap,
						matrix,
						eObject,
						exportOptions);
				// @formatter:on

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					try {

						// @formatter:off
						populateMatrixWithDataForEReference(
								matrixNameToMatrixMap,
								eObjectsIdentifiers,
								exportOptions,
								eObject,
								r);
						// @formatter:on

					} catch (EMFExportException e) {
						e.printStackTrace();
					}
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void populateMatrixWithDataForEReference(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Set<String> eObjectsIdentifiers, Map<Object, Object> exportOptions, EObject eObject, EReference r)
			throws EMFExportException {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany()) {

				// @formatter:off
				populateMatrixWithData(
						matrixNameToMatrixMap,
						eObjectsIdentifiers,
						exportOptions,
						(EObject) value);
				// @formatter:on

			} else if (r.isMany()) {

				// @formatter:off
				populateMatrixWithData(
						matrixNameToMatrixMap,
						eObjectsIdentifiers,
						exportOptions,
						((List<EObject>) value).toArray(EObject[]::new));
				// @formatter:on
			}
		}
	}

	private void populateMatrixWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> matrix, EObject eObject, Map<Object, Object> exportOptions)
			throws EMFExportException {

		String matrixName = constructEClassMatrixName(eObject.eClass());

		logger.debug("Creating data for matrix named '{}'", matrixName);

		List<EStructuralFeature> eAllStructuralFeatures = eObject.eClass().getEAllStructuralFeatures();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		boolean hasIDOrPseudoID = hasIDOrPseudoID(eObject);

		if (hasIDOrPseudoID) {
			// @formatter:off
			setIDValueCell(
					matrix, 
					matrixName,
					rowIndex, 
					0,
					getIDOrPseudoID(eObject));
			// @formatter:on
		}

		Iterator<EStructuralFeature> eAllStructuralFeaturesIt = eAllStructuralFeatures.iterator();

		int colIndex = 0;

		while (eAllStructuralFeaturesIt.hasNext()) {
			EStructuralFeature eStructuralFeature = eAllStructuralFeaturesIt.next();

			if (skipFeature(eObject, eStructuralFeature)) {
				continue;
			}

			// @formatter:off
			populateMatrixCellWithData(
					matrixNameToMatrixMap,
					matrix,
					rowIndex, 
					(hasIDOrPseudoID ? (colIndex + 1) : colIndex),
					eObject,
					eStructuralFeature, 
					exportOptions);
			// @formatter:on

			colIndex++;
		}
	}

	private void setIDValueCell(Table<Integer, Integer, Object> matrix, String matrixName, int rowIndex, int colIndex,
			String value) {
		refMatrixRowKeyIndex.put(new EMFExportRefMatrixNameIDCompositeKey(matrixName, value),
				getMatrixRowKey(rowIndex));

		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), new EMFExportEObjectIDValueCell(value));
	}

	private Table<Integer, Integer, Object> getMatrix(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, EClass eClass)
			throws EMFExportException {
		String matrixName = constructEClassMatrixName(eClass);

		if (!matrixNameToMatrixMap.containsKey(matrixName)) {
			throw new EMFExportException("Matrix '" + matrixName + "' does not exist!");
		}

		return matrixNameToMatrixMap.get(matrixName);
	}

	@SuppressWarnings("unchecked")
	private void populateMatrixCellWithData(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex, EObject eObject,
			EStructuralFeature eStructuralFeature, Map<Object, Object> exportOptions) throws EMFExportException {

		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			Object value = eObject.eGet(eAttribute);

			if ((value == null) || (eAttribute.isMany() && ((Collection<EObject>) value).isEmpty())) {
				setVoidValueCell(matrix, rowIndex, colIndex);

			} else {

				if (!eAttribute.isMany()) {

					if (value instanceof Date) {
						setDateValueCell(matrix, rowIndex, colIndex, (Date) value);

					} else if (value instanceof Number) {
						setNumberValueCell(matrix, rowIndex, colIndex, (Number) value);

					} else if (value instanceof Boolean) {
						setBooleanValueCell(matrix, rowIndex, colIndex, (Boolean) value);

					} else {
						setStringValueCell(matrix, rowIndex, colIndex,
								EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
					}

				} else {
					setMultiValueCell(matrix, rowIndex, colIndex, eAttribute, value);
				}
			}

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			// @formatter:off
			setEReferenceValueCell(
					matrixNameToMatrixMap,
					matrix, 
					rowIndex, 
					colIndex, 
					eObject, 
					eReference, 
					exportOptions);
			// @formatter:on
		}
	}

	private void setStringValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex, String value) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), value);
	}

	private void setDateValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex, Date value) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), value);
	}

	private void setNumberValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex, Number value) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), value.floatValue());
	}

	private void setBooleanValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			Boolean value) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), value);
	}

	@SuppressWarnings("unchecked")
	private void setMultiValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EAttribute eAttribute, Object multiValue) {
		StringBuilder sb = new StringBuilder();

		Collection<Object> values = (Collection<Object>) multiValue;

		if (!values.isEmpty()) {
			Iterator<Object> valuesIt = values.iterator();

			while (valuesIt.hasNext()) {
				sb.append(EcoreUtil.convertToString(eAttribute.getEAttributeType(), valuesIt.next()));

				if (valuesIt.hasNext()) {
					sb.append(System.lineSeparator());
				}
			}
		}

		// @formatter:off
		setStringValueCell(
				matrix,
				rowIndex,
				colIndex, 
				sb.toString());
		// @formatter:on
	}

	private void setVoidValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), Optional.empty());
	}

	@SuppressWarnings("unchecked")
	private void setEReferenceValueCell(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex, EObject eObject, EReference r,
			Map<Object, Object> exportOptions) throws EMFExportException {
		Object value = eObject.eGet(r);

		String refMatrixName = constructEClassMatrixName(r.getEReferenceType());

		if (!r.isMany() && (value == null)) {
			setEmptyOneEReferenceValueCell(matrix, refMatrixName, rowIndex, colIndex);
			return;

		} else if ((r.isMany() && (value == null))
				|| (r.isMany() && (value != null) && ((List<EObject>) value).isEmpty())) {
			setEmptyManyEReferencesValueCell(matrix, refMatrixName, rowIndex, colIndex);
			return;
		}

		if (!r.isMany()) {

			// @formatter:off
			setOneEReferenceValueCell(
					matrix, 
					refMatrixName,
					rowIndex,
					colIndex,
					(EObject) value,
					exportOptions);
			// @formatter:on

		} else if (r.isMany()) {

			if (addMappingTableEnabled(exportOptions)
					&& (!((List<EObject>) value).isEmpty() && (((List<EObject>) value).size() > 1))) {

				// @formatter:off
				populateEReferencesMappingMatrixWithData(
						matrixNameToMatrixMap,
						matrix,
						rowIndex, 
						colIndex,
						eObject, 
						r, 
						((List<EObject>) value), 
						exportOptions);
				// @formatter:on

			} else {

				// @formatter:off
				setManyEReferencesValueCell(
						matrix, 
						refMatrixName,
						rowIndex,
						colIndex, 
						((List<EObject>) value), 
						exportOptions);
				// @formatter:on
			}
		}
	}
	
	private void setOneEReferenceValueCell(Table<Integer, Integer, Object> matrix, String refMatrixName, int rowIndex,
			int colIndex, EObject eObject, Map<Object, Object> exportOptions) throws EMFExportException {

		if (hasIDOrPseudoID(eObject)) {

			// @formatter:off
			setOneEReferenceValueCell(
					matrix, 
					refMatrixName,
					rowIndex,
					colIndex, 
					getIDOrPseudoID(eObject), 
					getURI(eObject));
			// @formatter:on

		} else {
			setEmptyOneEReferenceValueCell(matrix, refMatrixName, rowIndex, colIndex);
		}		
	}
	
	private void setManyEReferencesValueCell(Table<Integer, Integer, Object> matrix, String refMatrixName, int rowIndex,
			int colIndex, List<EObject> eObjects, Map<Object, Object> exportOptions) throws EMFExportException {

		List<String> refIDs = new ArrayList<String>();
		List<String> refURIs = new ArrayList<String>();

		if (eObjects != null) {
			for (int i = 0; i < eObjects.size(); i++) {
				EObject eObject = eObjects.get(i);
				
				refIDs.add(getIDOrPseudoID(eObject));
				refURIs.add(getURI(eObject));
			}
		}
		
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex),
				new EMFExportEObjectManyReferencesValueCell(refMatrixName, refIDs, refURIs));
	}
	
	private void setOneEReferenceValueCell(Table<Integer, Integer, Object> matrix, String refMatrixName, int rowIndex,
			int colIndex, String refID, String refURI) throws EMFExportException {

		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex),
				new EMFExportEObjectOneReferenceValueCell(refMatrixName, refID, refURI));
	}

	private void setEmptyOneEReferenceValueCell(Table<Integer, Integer, Object> matrix, String refMatrixName,
			int rowIndex, int colIndex) {

		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex),
				new EMFExportEObjectOneReferenceValueCell(refMatrixName, null, null));
	}
	
	private void setEmptyManyEReferencesValueCell(Table<Integer, Integer, Object> matrix, String refMatrixName,
			int rowIndex, int colIndex) {

		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex),
				new EMFExportEObjectManyReferencesValueCell(refMatrixName, null, null));
	}

	private void populateEReferencesMappingMatrixWithData(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap, Table<Integer, Integer, Object> matrix,
			int rowIndex, int colIndex, EObject fromEObject, EReference toEReference, List<EObject> toEObjects,
			Map<Object, Object> exportOptions) throws EMFExportException {

		String eReferencesMappingMatrixName = constructEReferencesMappingMatrixName(fromEObject.eClass(),
				toEReference.getName());

		if (matrixNameToMatrixMap.containsKey(eReferencesMappingMatrixName)) {
			Table<Integer, Integer, Object> eReferencesMappingMatrix = matrixNameToMatrixMap
					.get(eReferencesMappingMatrixName);

			// @formatter:off
			setReferenceToEReferencesMappingMatrix(
					matrix, 
					rowIndex, 
					colIndex, 
					eReferencesMappingMatrixName);
			// @formatter:on

			// @formatter:off
			populateEReferencesMappingMatrixWithData(
					eReferencesMappingMatrix, 
					fromEObject, 
					toEObjects, 
					exportOptions);
			// @formatter:on

		} else {
			throw new EMFExportException("No mapping matrix named '" + eReferencesMappingMatrixName + "'");
		}
	}

	private void setReferenceToEReferencesMappingMatrix(Table<Integer, Integer, Object> matrix, int rowIndex,
			int colIndex, String eReferencesMappingMatrixName) {

		// @formatter:off
		setMappingMatrixReferenceValueCell(
				matrix, 
				rowIndex, 
				colIndex,
				eReferencesMappingMatrixName);
		// @formatter:on
	}

	private void setMappingMatrixReferenceValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			String eReferencesMappingMatrixName) {
		matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex),
				new EMFExportMappingMatrixReferenceValueCell(eReferencesMappingMatrixName,
						constructEReferencesMappingMatrixEReferenceValue(eReferencesMappingMatrixName)));
	}

	private String constructEReferencesMappingMatrixEReferenceValue(String eReferencesMappingMatrixName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append("See: ");
		sb.append(eReferencesMappingMatrixName);
		return sb.toString();
	}

	private void populateEReferencesMappingMatrixWithData(Table<Integer, Integer, Object> eReferencesMappingMatrix,
			EObject fromEObject, List<EObject> toEObjects, Map<Object, Object> exportOptions)
			throws EMFExportException {

		for (EObject toEObject : toEObjects) {

			// @formatter:off
			populateEReferencesMappingMatrixRowWithData(
					eReferencesMappingMatrix, 
					fromEObject, 
					toEObject, 
					exportOptions);
			// @formatter:on
		}
	}

	private void populateEReferencesMappingMatrixRowWithData(Table<Integer, Integer, Object> eReferencesMappingMatrix,
			EObject fromEObject, EObject toEObject, Map<Object, Object> exportOptions) throws EMFExportException {

		int rowsCount = eReferencesMappingMatrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		// @formatter:off
		setOneEReferenceValueCell(
				eReferencesMappingMatrix, 
				constructEClassMatrixName(fromEObject.eClass()),
				rowIndex, 
				0, 
				fromEObject, 
				exportOptions);
		// @formatter:on

		// @formatter:off
		setOneEReferenceValueCell(
				eReferencesMappingMatrix, 
				constructEClassMatrixName(toEObject.eClass()),
				rowIndex, 
				1, 
				toEObject, 
				exportOptions);
		// @formatter:on
	}

	private void constructMetadataMatrixIfNotExists(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EClass eClass) {

		String eClassMetadataMatrixName = constructEClassMetadataMatrixName(eClass);

		if (!matrixNameToMatrixMap.containsKey(eClassMetadataMatrixName)) {
			logger.debug("Creating metadata matrix named '{}'", eClassMetadataMatrixName);

			Table<Integer, Integer, Object> eClassMetadataMatrix = HashBasedTable.create();

			maybeSetEClassMetadataDocumentation(eClassMetadataMatrix, eClass);

			constructEClassMetadataMatrixColumnHeaders(eClassMetadataMatrix);

			matrixNameToMatrixMap.put(eClassMetadataMatrixName, eClassMetadataMatrix);
		}
	}

	private void constructMetadataMatrixIfNotExists(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap,
			EEnum eEnum) {

		String eEnumMetadataMatrixName = constructEEnumMetadataMatrixName(eEnum);

		if (!matrixNameToMatrixMap.containsKey(eEnumMetadataMatrixName)) {
			logger.debug("Creating metadata matrix named '{}'", eEnumMetadataMatrixName);

			Table<Integer, Integer, Object> eEnumMetadataMatrix = HashBasedTable.create();

			maybeSetEEnumMetadataDocumentation(eEnumMetadataMatrix, eEnum);

			constructEEnumMetadataMatrixColumnHeaders(eEnumMetadataMatrix);

			matrixNameToMatrixMap.put(eEnumMetadataMatrixName, eEnumMetadataMatrix);
		}
	}

	private String constructEClassMetadataMatrixName(EClass eClass) {
		return constructMetadataMatrixName(constructEClassMatrixName(eClass));
	}

	private String constructEEnumMetadataMatrixName(EEnum eEnum) {
		return constructMetadataMatrixName(constructEEnumMatrixName(eEnum));
	}

	private String constructMetadataMatrixName(String metadataMatrixName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append(metadataMatrixName);
		sb.append(" ");
		sb.append("(");
		sb.append(METADATA_MATRIX_NAME_SUFFIX);
		sb.append(")");
		return createSafeMatrixName(sb.toString());
	}

	private void populateMatricesWithMetadata(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap)
			throws EMFExportException {

		resetStopwatch();

		logger.info("Starting populating matrices with metadata");

		populateMatricesWithEClassesMetadata(matrixNameToMatrixMap);

		populateMatricesWithEEnumsMetadata(matrixNameToMatrixMap);

		logger.info("Finished populating matrices with metadata in {} second(s)", elapsedTimeInSeconds());
	}

	private void populateMatricesWithEClassesMetadata(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) throws EMFExportException {

		for (EClass eClass : this.eObjectsClasses) {
			String eClassMetadataMatrixName = constructEClassMetadataMatrixName(eClass);

			if (matrixNameToMatrixMap.containsKey(eClassMetadataMatrixName)) {
				Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(eClassMetadataMatrixName);

				populateMatrixWithEClassMetadata(matrix, eClass);

			} else {
				throw new EMFExportException("No metadata matrix for EClass named '" + eClassMetadataMatrixName + "'");
			}
		}
	}

	private void populateMatricesWithEEnumsMetadata(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap)
			throws EMFExportException {

		for (EEnum eEnum : this.eObjectsEnums) {
			String eEnumMetadataMatrixName = constructEEnumMetadataMatrixName(eEnum);

			if (matrixNameToMatrixMap.containsKey(eEnumMetadataMatrixName)) {
				Table<Integer, Integer, Object> matrix = matrixNameToMatrixMap.get(eEnumMetadataMatrixName);

				populateMatrixWithEEnumMetadata(matrix, eEnum);

			} else {
				throw new EMFExportException("No metadata matrix for EEnum named '" + eEnumMetadataMatrixName + "'");
			}
		}
	}

	private void maybeSetEClassMetadataDocumentation(Table<Integer, Integer, Object> matrix, EClass eClass) {
		EAnnotation genModelAnnotation = eClass.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(matrix, genModelAnnotation);
		}
	}

	private void maybeSetEEnumMetadataDocumentation(Table<Integer, Integer, Object> matrix, EEnum eEnum) {
		EAnnotation genModelAnnotation = eEnum.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(matrix, genModelAnnotation);
		}
	}

	private void setTypeLevelMetadataDocumentation(Table<Integer, Integer, Object> matrix,
			EAnnotation genModelAnnotation) {
		Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

		if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {

			matrix.put(getMatrixRowKey(1), getMatrixColumnKey(0), METADATA_DOCUMENTATION_HEADER);

			matrix.put(getMatrixRowKey(1), getMatrixColumnKey(1),
					genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS));
		}
	}

	private void constructEClassMetadataMatrixColumnHeaders(Table<Integer, Integer, Object> matrix) {
		constructMetadataMatrixColumnHeaders(matrix, METADATA_ECLASS_MATRIX_COLUMNS_HEADERS);
	}

	private void constructEEnumMetadataMatrixColumnHeaders(Table<Integer, Integer, Object> matrix) {
		constructMetadataMatrixColumnHeaders(matrix, METADATA_EENUM_MATRIX_COLUMNS_HEADERS);
	}

	private void constructMetadataMatrixColumnHeaders(Table<Integer, Integer, Object> matrix, List<String> headers) {
		int columnsCount = headers.size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			matrix.put(getMatrixRowKey(rowIndex), getMatrixColumnKey(colIndex), headers.get(colIndex));
		}
	}

	private void populateMatrixWithEClassMetadata(Table<Integer, Integer, Object> matrix, EClass eClass) {
		if (eObjectsClassesWithPseudoIDs.contains(eClass)) {
			setEClassMetadataPseudoIDValueCell(matrix);
		}

		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {
			populateEClassMetadataMatrixRowWithData(matrix, eStructuralFeature);
		});
	}

	private void setEClassMetadataPseudoIDValueCell(Table<Integer, Integer, Object> matrix) {
		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		// 6 columns: Name | Type | isMany | isRequired | Default value | Documentation
		setStringValueCell(matrix, rowIndex, 0, ID_ATTRIBUTE_NAME);
		setStringValueCell(matrix, rowIndex, 1, "String");
		setBooleanValueCell(matrix, rowIndex, 2, Boolean.FALSE);
		setBooleanValueCell(matrix, rowIndex, 3, Boolean.FALSE);
		setVoidValueCell(matrix, rowIndex, 4);
		setStringValueCell(matrix, rowIndex, 5, METADATA_PSEUDOID_DOCUMENTATION);
	}

	private void populateMatrixWithEEnumMetadata(Table<Integer, Integer, Object> matrix, EEnum eEnum) {
		eEnum.getELiterals().forEach(eEnumLiteral -> {
			populateEEnumMetadataMatrixRowWithData(matrix, eEnumLiteral);
		});
	}

	private void populateEClassMetadataMatrixRowWithData(Table<Integer, Integer, Object> matrix,
			EStructuralFeature eStructuralFeature) {

		int columnsCount = matrix.columnKeySet().size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			populateEClassMetadataCellWithData(matrix, rowIndex, colIndex, eStructuralFeature);
		}
	}

	private void populateEEnumMetadataMatrixRowWithData(Table<Integer, Integer, Object> matrix,
			EEnumLiteral eEnumLiteral) {

		int columnsCount = matrix.columnKeySet().size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			populateEEnumMetadataCellWithData(matrix, rowIndex, colIndex, eEnumLiteral);
		}
	}

	private void populateEClassMetadataCellWithData(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		switch (colIndex) {
		case 0: // Name
			setEClassMetadataNameValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		case 1: // Type
			setEClassMetadataTypeValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		case 2: // isMany
			setEClassMetadataIsManyValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		case 3: // isRequired
			setEClassMetadataIsRequiredValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		case 4: // Default value
			setEClassMetadataDefaultValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		case 5: // Documentation
			setEStructuralFeatureMetadataDocumentationValueCell(matrix, rowIndex, colIndex, eStructuralFeature);
			break;
		}
	}

	private void populateEEnumMetadataCellWithData(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		switch (colIndex) {
		case 0: // Name
			setEEnumMetadataNameValueCell(matrix, rowIndex, colIndex, eEnumLiteral);
			break;
		case 1: // Literal
			setEEnumMetadataLiteralValueCell(matrix, rowIndex, colIndex, eEnumLiteral);
			break;
		case 2: // Value
			setEEnumMetadataValueValueCell(matrix, rowIndex, colIndex, eEnumLiteral);
			break;
		case 3: // Documentation
			setEEnumLiteralMetadataDocumentationValueCell(matrix, rowIndex, colIndex, eEnumLiteral);
			break;
		}
	}

	private void setEClassMetadataNameValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setStringValueCell(matrix, rowIndex, colIndex, eStructuralFeature.getName());
	}

	private void setEClassMetadataTypeValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			setStringValueCell(matrix, rowIndex, colIndex,
					normalizeMetadataTypeEAttributeName(eAttribute.getEAttributeType()));

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			setStringValueCell(matrix, rowIndex, colIndex, eReference.getEReferenceType().getName());

		} else {
			setVoidValueCell(matrix, rowIndex, colIndex);
		}
	}

	private String normalizeMetadataTypeEAttributeName(EDataType eAttributeType) {
		if (isEcoreDataType(eAttributeType)) {
			String instanceClassName = eAttributeType.getInstanceClassName();
			try {
				return Class.forName(instanceClassName).getSimpleName();
			} catch (ClassNotFoundException e) {
				return instanceClassName;
			}
		} else {
			return eAttributeType.getName();
		}
	}

	private boolean isEcoreDataType(EDataType eAttributeType) {
		return eAttributeType.getEPackage().getName().equalsIgnoreCase(ECORE_PACKAGE_NAME);
	}

	private void setEClassMetadataIsManyValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(matrix, rowIndex, colIndex, eStructuralFeature.isMany());
	}

	private void setEClassMetadataIsRequiredValueCell(Table<Integer, Integer, Object> matrix, int rowIndex,
			int colIndex, EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(matrix, rowIndex, colIndex, eStructuralFeature.isRequired());
	}

	private void setEClassMetadataDefaultValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			if (eAttribute.getDefaultValue() != null) {
				setStringValueCell(matrix, rowIndex, colIndex,
						EcoreUtil.convertToString(eAttribute.getEAttributeType(), eAttribute.getDefaultValue()));
				return;
			}
		}

		setVoidValueCell(matrix, rowIndex, colIndex);
	}

	private void setEStructuralFeatureMetadataDocumentationValueCell(Table<Integer, Integer, Object> matrix,
			int rowIndex, int colIndex, EStructuralFeature eStructuralFeature) {
		EAnnotation genModelAnnotation = eStructuralFeature.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(matrix, rowIndex, colIndex, genModelAnnotation);
	}

	private void setEEnumMetadataNameValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setStringValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getName());
	}

	private void setEEnumMetadataLiteralValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setStringValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getLiteral());
	}

	private void setEEnumMetadataValueValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setNumberValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getValue());
	}

	private void setEEnumLiteralMetadataDocumentationValueCell(Table<Integer, Integer, Object> matrix, int rowIndex,
			int colIndex, EEnumLiteral eEnumLiteral) {
		EAnnotation genModelAnnotation = eEnumLiteral.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(matrix, rowIndex, colIndex, genModelAnnotation);
	}

	private void setMetadataDocumentationValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			EAnnotation genModelAnnotation) {
		if (genModelAnnotation != null) {
			Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

			if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {
				setMetadataDocumentationValueCell(matrix, rowIndex, colIndex,
						genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS));
			}
		} else {
			setVoidValueCell(matrix, rowIndex, colIndex);
		}
	}

	private void setMetadataDocumentationValueCell(Table<Integer, Integer, Object> matrix, int rowIndex, int colIndex,
			String documentation) {
		setStringValueCell(matrix, rowIndex, colIndex, documentation);
	}

	private boolean skipFeature(EObject eObject, EStructuralFeature eStructuralFeature) {
		if ((eStructuralFeature.getName()).equalsIgnoreCase(ID_ATTRIBUTE_NAME) || eStructuralFeature.isTransient()) {
			return true;

		} else if ((eStructuralFeature instanceof EAttribute)
				&& ((EAttribute) eStructuralFeature).getEAttributeType().getInstanceClass() == byte[].class) {
			return true;
		}

		return false;
	}

	private void generatePseudoIDs(List<? extends EObject> eObjects) {
		resetStopwatch();

		logger.info("Starting generation of pseudo IDs");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		generatePseudoIDs(eObjects, processedEObjectsIdentifiers);

		logger.info("Finished generation of pseudo IDs in {} second(s)", elapsedTimeInSeconds());
	}

	private void generatePseudoIDs(List<? extends EObject> eObjects, Set<String> processedEObjectsIdentifiers) {
		for (EObject eObject : eObjects) {
			if (isProcessed(processedEObjectsIdentifiers, eObject)) {
				continue;
			}

			processedEObjectsIdentifiers.add(getEObjectIdentifier(eObject));

			generatePseudoID(eObject);

			eObject.eClass().getEAllReferences().stream().forEach(eReference -> {
				generatePseudoID(eObject, eReference, processedEObjectsIdentifiers);
			});
		}
	}

	@SuppressWarnings("unchecked")
	private void generatePseudoID(EObject eObject, EReference eReference, Set<String> processedEObjectsIdentifiers) {
		Object value = eObject.eGet(eReference);
		if (value != null) {
			if (!eReference.isMany()) {
				generatePseudoID((EObject) value);
			} else if (eReference.isMany()) {
				generatePseudoIDs((List<EObject>) value, processedEObjectsIdentifiers);
			}
		}
	}

	private void generatePseudoID(EObject eObject) {
		if (!hasIDOrPseudoID(eObject)) {
			logger.debug("Generating pseudo ID for EObject ID '{}' named '{}'", getEObjectIdentifier(eObject),
					eObject.eClass().getName());
			this.eObjectUniqueIdentifierToPseudoIDMap.put(getEObjectIdentifier(eObject), UUID.randomUUID().toString());
			this.eObjectsClassesWithPseudoIDs.add(eObject.eClass());
		}
	}

	private String getEObjectIdentifier(EObject eObject) {
		return EcoreUtil.getIdentification(eObject);
	}

	protected boolean hasIDOrPseudoID(EObject eObject) {
		return (hasID(eObject) || hasPseudoID(eObject));
	}

	protected boolean hasID(EObject eObject) {
		return (getID(eObject) != null);
	}

	protected String getID(EObject eObject) {
		String id = EcoreUtil.getID(eObject);
		if (id != null) {
			return id;
		}

		// in some models (e.g. '/org.gecko.emf.util.model/model/utilities.ecore') ID
		// attributes are present but not marked as ID
		EStructuralFeature idEStructuralFeature = eObject.eClass().getEStructuralFeature(ID_ATTRIBUTE_NAME);
		if (idEStructuralFeature != null) {
			return String.valueOf(eObject.eGet(idEStructuralFeature));
		}

		return null;
	}

	protected boolean hasPseudoID(EObject eObject) {
		return (this.eObjectUniqueIdentifierToPseudoIDMap.containsKey(getEObjectIdentifier(eObject)));
	}

	protected String getPseudoID(EObject eObject) {
		return this.eObjectUniqueIdentifierToPseudoIDMap.get(getEObjectIdentifier(eObject));
	}

	protected String getIDOrPseudoID(EObject eObject) {
		String id = getID(eObject);
		if (id != null) {
			return id;
		}

		return getPseudoID(eObject);
	}

	protected String getURI(EObject eObject) {
		if (eObject != null) {
			return EcoreUtil.getURI(eObject).toString();
		} else {
			return null;
		}
	}

	private String constructMatrixColumnHeaderName(EStructuralFeature eStructuralFeature) {
		StringBuilder sb = new StringBuilder(100);
		if (eStructuralFeature instanceof EReference) {
			sb.append(REF_COLUMN_PREFIX);
		}
		sb.append(eStructuralFeature.getName());
		return sb.toString();
	}

	protected Integer getMatrixRowKey(int rowIndex) {
		return Integer.valueOf(rowIndex);
	}

	protected Integer getMatrixColumnKey(int colIndex) {
		return Integer.valueOf(colIndex);
	}

	private String constructEClassMatrixName(EClass eClass) {
		return createSafeMatrixName(eClass.getName());
	}

	private String constructEEnumMatrixName(EEnum eEnum) {
		return createSafeMatrixName(eEnum.getName());
	}

	/**
	 * Based on
	 * {@link org.apache.poi.ss.util.WorkbookUtil#createSafeSheetName(String)}
	 **/
	private String createSafeMatrixName(final String nameProposal) {
		return createSafeMatrixName(nameProposal, ' ');
	}

	/**
	 * Based on
	 * {@link org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(String, char)}
	 **/
	private String createSafeMatrixName(final String nameProposal, char replaceChar) {
		if (nameProposal == null) {
			return "null";
		}
		if (nameProposal.length() < 1) {
			return "empty";
		}
		final int length = Math.min(31, nameProposal.length());
		final String shortenname = nameProposal.substring(0, length);
		final StringBuilder result = new StringBuilder(shortenname);
		for (int i = 0; i < length; i++) {
			char ch = result.charAt(i);
			switch (ch) {
			case '\u0000':
			case '\u0003':
			case ':':
			case '/':
			case '\\':
			case '?':
			case '*':
			case ']':
			case '[':
				result.setCharAt(i, replaceChar);
				break;
			case '\'':
				if (i == 0 || i == length - 1) {
					result.setCharAt(i, replaceChar);
				}
				break;
			default:
				// all other chars OK
			}
		}
		return result.toString();
	}

	private boolean isEcoreEEnumDataType(EStructuralFeature eStructuralFeature) {
		return (eStructuralFeature instanceof EAttribute
				&& ((EAttribute) eStructuralFeature).getEAttributeType() instanceof EEnum);
	}

	private EEnum extractEEnumDataType(EStructuralFeature eStructuralFeature) {
		return ((EEnum) ((EAttribute) eStructuralFeature).getEAttributeType());
	}

	protected Map<String, Table<Integer, Integer, Object>> eObjectMatricesOnly(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) {
		// @formatter:off
		return matrixNameToMatrixMap.entrySet().stream()
				.filter(entry -> (!isMetadataMatrix(entry.getKey()) && !isMappingMatrix(entry.getKey())))
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		// @formatter:on
	}

	protected Map<String, Table<Integer, Integer, Object>> metadataMatricesOnly(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) {
		// @formatter:off
		return matrixNameToMatrixMap.entrySet().stream()
				.filter(entry -> isMetadataMatrix(entry.getKey()))
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		// @formatter:on
	}

	protected Map<String, Table<Integer, Integer, Object>> mappingMatricesOnly(
			Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap) {
		// @formatter:off
		return matrixNameToMatrixMap.entrySet().stream()
				.filter(entry -> isMappingMatrix(entry.getKey()))
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		// @formatter:on
	}

	private boolean isMetadataMatrix(String matrixName) {
		return matrixName.contains("(" + METADATA_MATRIX_NAME_SUFFIX + ")");
	}

	private boolean isMappingMatrix(String matrixName) {
		return matrixName.contains("(" + MAPPING_MATRIX_NAME_SUFFIX + ")");
	}

	private boolean isProcessed(Set<String> eObjectsIdentifiers, EObject eObject) {
		return eObjectsIdentifiers.contains(getEObjectIdentifier(eObject));
	}

	protected void validateMatricesColumnsSize(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap)
			throws EMFExportException {
		for (String matrixName : matrixNameToMatrixMap.keySet()) {
			validateMatrixColumnsSize(matrixNameToMatrixMap.get(matrixName), matrixName);
		}
	}

	protected void validateMatrixColumnsSize(Table<Integer, Integer, Object> matrix, String matrixName)
			throws EMFExportException {

		if (!matrix.isEmpty()) {
			int columnsCount = matrix.columnKeySet().size();

			if (columnsCount > MAX_COLUMNS) {
				throw new EMFExportException(String.format(
						"Number of columns %d in matrix named '%s' exceeds maximum number of columns (%d) allowed!",
						columnsCount, matrixName, MAX_COLUMNS));
			}
		}
	}

	protected void validateMatricesRowsSize(Map<String, Table<Integer, Integer, Object>> matrixNameToMatrixMap)
			throws EMFExportException {
		for (String matrixName : matrixNameToMatrixMap.keySet()) {
			validateMatrixRowsSize(matrixNameToMatrixMap.get(matrixName), matrixName);
		}
	}

	protected void validateMatrixRowsSize(Table<Integer, Integer, Object> matrix, String matrixName)
			throws EMFExportException {

		if (!matrix.isEmpty()) {
			int rowsCount = matrix.rowKeySet().size();

			if (rowsCount > MAX_ROWS) {
				throw new EMFExportException(String.format(
						"Number of rows %d in matrix named '%s' exceeds maximum number of rows (%d) allowed!",
						rowsCount, matrixName, MAX_ROWS));
			}
		}
	}

	protected Map<Object, Object> validateExportOptions(Map<?, ?> options) throws EMFExportException {
		if (options == null) {
			throw new EMFExportException("Please specify export options!");
		}

		Map<Object, Object> exportOptions = Map.copyOf(options);

		if (!exportNonContainmentEnabled(exportOptions) && addMappingTableEnabled(exportOptions)) {
			throw new EMFExportException(
					"Incompatible combination of export options: 'export non-containment references' option cannot be turned off if 'generate mapping table' option is turned on!");
		}

		return exportOptions;
	}

	protected Locale locale(Map<Object, Object> exportOptions) {
		return ((Locale) exportOptions.getOrDefault(EMFExportOptions.OPTION_LOCALE, Locale.getDefault()));
	}

	protected boolean exportNonContainmentEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, Boolean.TRUE));
	}

	protected boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.TRUE));
	}

	protected boolean addMappingTableEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADD_MAPPING_TABLE, Boolean.TRUE));
	}

	protected boolean showURIs(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_SHOW_URIS, Boolean.TRUE));
	}

	protected void resetStopwatch() {
		this.stopwatch.reset().start();
	}

	protected double elapsedTimeInSeconds() {
		return (double) this.stopwatch.elapsed().toNanos() / 1000000000.0;
	}

	protected void resetState() {
		this.eObjectIDToMatrixNameMap.clear();
		this.eObjectUniqueIdentifierToPseudoIDMap.clear();
		this.eObjectsClassesWithPseudoIDs.clear();
		this.eObjectsClasses.clear();
		this.eObjectsEnums.clear();
		this.refMatrixRowKeyIndex.clear();
	}
}
