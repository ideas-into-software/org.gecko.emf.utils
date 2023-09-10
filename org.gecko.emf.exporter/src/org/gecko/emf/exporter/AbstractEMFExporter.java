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
	private final Logger logger;
	protected final Stopwatch stopwatch;
	
	// TODO: refactor so other accumulating data structures are also kept on state instead of being passed via method args
	
	// maps EObjects' IDs to names of matrices, so those can be looked up e.g. when constructing links
	protected final Map<String, String> eObjectIDToMatrixNameMap;

	protected static final List<String> METADATA_ECLASS_MATRIX_COLUMNS_HEADERS = List.of("Name", "Type", "isMany",
			"isRequired", "Default value", "Documentation");

	protected static final List<String> METADATA_EENUM_MATRIX_COLUMNS_HEADERS = List.of("Name", "Literal", "Value",
			"Documentation");
	protected static final String METADATA_DOCUMENTATION_HEADER = "Documentation";

	protected static final String METADATA_MATRIX_NAME_SUFFIX = "Metadata";
	protected static final String MAPPING_MATRIX_NAME_SUFFIX = "Mapping";

	protected static final String DOCUMENTATION_GENMODEL_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";
	protected static final String DOCUMENTATION_GENMODEL_DETAILS = "documentation";

	protected static final String ECORE_PACKAGE_NAME = "ecore";

	protected static final String ID_COLUMN_NAME = "Id";

	protected static final String ID_ATTRIBUTE_NAME = "id";

	protected static final String REF_COLUMN_PREFIX = "ref_";

	// TODO: handle cases where there are more than 26 columns
	protected static final char[] MATRIX_COLUMNS_HEADERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

	protected AbstractEMFExporter(Logger logger, Stopwatch stopwatch) {
		this.logger = logger;
		this.stopwatch = stopwatch;
		this.eObjectIDToMatrixNameMap = new HashMap<String, String>();
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
	protected Map<String, Table<Integer, Character, Object>> exportEObjectsToMatrices(List<EObject> eObjects,
			Map<?, ?> options) throws EMFExportException {
		Objects.requireNonNull(eObjects, "At least one EObject is required for export!");

		final Map<Object, Object> exportOptions = validateExportOptions(options);

		// stores EObjects' EClasses - used e.g. to construct meta data
		final Set<EClass> eObjectsClasses = new HashSet<EClass>();

		// stores EEnums - used e.g. to construct meta data
		final Set<EEnum> eObjectsEnums = new HashSet<EEnum>();

		// maps EObjects' unique identifiers to pseudo IDs - for those EObjects which
		// lack id field
		final Map<String, String> eObjectsPseudoIDs = new HashMap<String, String>();

		// pseudo IDs are needed before main processing starts
		generatePseudoIDs(eObjects, eObjectsPseudoIDs);

		// @formatter:off
		Map<String, Table<Integer, Character, Object>> mapOfMatrices = constructMatrices( 
				eObjectsClasses,
				eObjectsEnums, 
				eObjectsPseudoIDs,
				eObjects,
				exportOptions);
		// @formatter:on

		// @formatter:off
		populateMatricesWithData(
				mapOfMatrices,
				eObjectsPseudoIDs, 
				eObjects,
				exportOptions);
		// @formatter:on

		if (exportMetadataEnabled(exportOptions)) {
			// @formatter:off
			populateMatricesWithMetadata(
					mapOfMatrices, 
					eObjectsClasses, 
					eObjectsEnums);
			// @formatter:on
		}

		return mapOfMatrices;
	}

	private Map<String, Table<Integer, Character, Object>> constructMatrices(Set<EClass> eObjectsClasses,
			Set<EEnum> eObjectsEnums, Map<String, String> eObjectsPseudoIDs, List<EObject> eObjects,
			Map<Object, Object> exportOptions) {

		resetStopwatch();

		logger.info("Starting construction of matrices");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		Map<String, Table<Integer, Character, Object>> mapOfMatrices = new HashMap<String, Table<Integer, Character, Object>>();

		for (EObject eObject : eObjects) {

			// @formatter:off
			constructMatrixForEObjectWithEReferences(
					mapOfMatrices,
					processedEObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs, 
					exportOptions,
					eObject);
			// @formatter:on
		}

		logger.info("Finished construction of matrices in {} second(s)", elapsedTimeInSeconds());

		return mapOfMatrices;
	}

	private void constructMatrixForEObjectWithEReferences(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions, EObject eObject) {

		// @formatter:off
		constructMatrix(
				mapOfMatrices, 
				eObjectsIdentifiers, 
				eObjectsClasses, 
				eObjectsEnums, 
				eObjectsPseudoIDs,
				exportOptions,
				eObject);
		// @formatter:on

		eObject.eClass().getEAllReferences().stream().forEach(r -> {

			// @formatter:off
			constructMatrixForEReference(
					mapOfMatrices, 
					eObjectsIdentifiers, 
					eObjectsClasses, 
					eObjectsEnums,
					eObjectsPseudoIDs, 
					exportOptions, 
					eObject, 
					r);
			// @formatter:on
		});
	}

	private void constructMatrix(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions, EObject... eObjects) {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			String matrixName = constructEClassMatrixName(eClass);

			constructMatrixIfNotExists(mapOfMatrices, matrixName, eClass, eObjects[0], eObjectsEnums,
					hasPseudoID(eObjects[0], eObjectsPseudoIDs), exportOptions);

			for (EObject eObject : eObjects) {
				String eObjectIdentifier = getEObjectIdentifier(eObject);

				eObjectsIdentifiers.add(eObjectIdentifier);
				eObjectsClasses.add(eObject.eClass());

				if (hasID(eObject)) {
					this.eObjectIDToMatrixNameMap.put(getID(eObject), matrixName);
				} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
					this.eObjectIDToMatrixNameMap.put(getPseudoID(eObject, eObjectsPseudoIDs), matrixName);
				}

				eObject.eClass().getEAllReferences().stream().forEach(r -> {

					// @formatter:off
					constructMatrixForEReference(
							mapOfMatrices,
							eObjectsIdentifiers, 
							eObjectsClasses, 
							eObjectsEnums,
							eObjectsPseudoIDs, 
							exportOptions, 
							eObject, 
							r);
					// @formatter:on
				});
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void constructMatrixForEReference(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<String> eObjectsIdentifiers, Set<EClass> eObjectsClasses, Set<EEnum> eObjectsEnums,
			Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions, EObject eObject, EReference r) {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {

				// @formatter:off
				constructMatrix(
						mapOfMatrices,
						eObjectsIdentifiers,
						eObjectsClasses, 
						eObjectsEnums, 
						eObjectsPseudoIDs,
						exportOptions,
						(EObject) value);
				// @formatter:on

			} else if (r.isMany()) {

				// @formatter:off
				constructMatrix(
						mapOfMatrices,
						eObjectsIdentifiers,
						eObjectsClasses, 
						eObjectsEnums, 
						eObjectsPseudoIDs,
						exportOptions,
						((List<EObject>) value).toArray(EObject[]::new));
				// @formatter:on

				if (!((List<EObject>) value).isEmpty() && (((List<EObject>) value).size() > 1)) {
					if (addMappingTableEnabled(exportOptions)) {

						// @formatter:off
						constructEReferencesMappingMatrixIfNotExists(
								mapOfMatrices, 
								eObject,
								r);
						// @formatter:on						
					}
				}
			}
		}
	}

	private void constructEReferencesMappingMatrixIfNotExists(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, EObject fromEObject,
			EReference toEReference) {
		String eReferencesMappingMatrixName = constructEReferencesMappingMatrixName(fromEObject.eClass(),
				toEReference.getName());

		if (!mapOfMatrices.containsKey(eReferencesMappingMatrixName)) {
			logger.debug("Creating EReferences mapping matrix named '{}'", eReferencesMappingMatrixName);

			Table<Integer, Character, Object> eReferencesMappingMatrix = HashBasedTable.create();

			// @formatter:off
			constructEReferencesMappingMatrixColumnHeaders(
					eReferencesMappingMatrix, 
					fromEObject.eClass(), 
					toEReference.getEReferenceType());
			// @formatter:on

			mapOfMatrices.put(eReferencesMappingMatrixName, eReferencesMappingMatrix);
		}
	}

	private void constructEReferencesMappingMatrixColumnHeaders(
			Table<Integer, Character, Object> eReferencesMappingMatrix, EClass fromEClass, EClass toEClass) {

		String fromEClassColumnHeaderName = constructEReferencesMappingMatrixColumnHeaderName(fromEClass);
		eReferencesMappingMatrix.put(Integer.valueOf(1), getMatrixColumnName(0), fromEClassColumnHeaderName);

		String toEClassColumnHeaderName = constructEReferencesMappingMatrixColumnHeaderName(toEClass);
		eReferencesMappingMatrix.put(Integer.valueOf(1), getMatrixColumnName(1), toEClassColumnHeaderName);
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

	private Table<Integer, Character, Object> constructMatrixIfNotExists(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, String matrixName, EClass eClass,
			EObject eObject, Set<EEnum> eObjectsEnums, boolean hasPseudoID, Map<Object, Object> exportOptions) {

		if (mapOfMatrices.containsKey(matrixName)) {
			logger.debug("Matrix named '{}' already exists!", matrixName);

			return mapOfMatrices.get(matrixName);

		} else {
			logger.debug("Matrix named '{}' does not exist yet, creating...", matrixName);

			// @formatter:off
			Table<Integer, Character, Object> matrix = constructMatrix(
					mapOfMatrices,
					matrixName, 
					eClass,
					eObject,
					eObjectsEnums, 
					hasPseudoID, 
					exportOptions);
			// @formatter:on

			mapOfMatrices.put(matrixName, matrix);

			if (exportMetadataEnabled(exportOptions)) {
				constructMetadataMatrixIfNotExists(mapOfMatrices, eClass);
			}

			return matrix;
		}
	}

	private Table<Integer, Character, Object> constructMatrix(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, String matrixName, EClass eClass,
			EObject eObject, Set<EEnum> eObjectsEnums, boolean hasPseudoID, Map<Object, Object> exportOptions) {

		Table<Integer, Character, Object> matrix = HashBasedTable.create();

		// @formatter:off
		constructMatrixColumnHeadersAndMetadataMatrixIfEnabled(
				mapOfMatrices,
				matrixName,
				matrix, 
				eClass, 
				eObject,
				eObjectsEnums, 
				hasPseudoID,
				exportOptions);
		// @formatter:on

		return matrix;
	}

	private void constructMatrixColumnHeadersAndMetadataMatrixIfEnabled(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, String matrixName,
			Table<Integer, Character, Object> matrix, EClass eClass, EObject eObject, Set<EEnum> eObjectsEnums,
			boolean hasPseudoID, Map<Object, Object> exportOptions) {

		logger.debug("Creating columns' headers for matrix named '{}'"
				+ (hasPseudoID ? " with pseudo ID column" : " without pseudo ID column"), matrixName);

		List<EStructuralFeature> eAllStructuralFeatures = eClass.getEAllStructuralFeatures();

		int columnsCount = eAllStructuralFeatures.size();

		logger.debug("Matrix named '{}' has {} column(s) based on number of structure features", matrixName,
				columnsCount);

		boolean hasIDOrPseudoID = hasPseudoID || hasID(eObject);

		if (hasIDOrPseudoID) {
			matrix.put(Integer.valueOf(1), getMatrixColumnName(0), ID_COLUMN_NAME);
		}

		Iterator<EStructuralFeature> eAllStructuralFeaturesIt = eAllStructuralFeatures.iterator();

		int colIndex = 0;

		while (eAllStructuralFeaturesIt.hasNext()) {
			EStructuralFeature eStructuralFeature = eAllStructuralFeaturesIt.next();

			if ((eStructuralFeature.getName()).equalsIgnoreCase(ID_ATTRIBUTE_NAME)) {
				continue;
			}

			if (isEcoreEEnumDataType(eStructuralFeature)) {
				EEnum eEnum = extractEEnumDataType(eStructuralFeature);

				eObjectsEnums.add(eEnum);

				if (exportMetadataEnabled(exportOptions)) {
					constructMetadataMatrixIfNotExists(mapOfMatrices, eEnum);
				}
			}

			String columnHeaderName = constructMatrixColumnHeaderName(eStructuralFeature);

			matrix.put(Integer.valueOf(1), getMatrixColumnName((hasIDOrPseudoID ? (colIndex + 1) : colIndex)),
					columnHeaderName);

			colIndex++;
		}
	}

	private void populateMatricesWithData(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Map<String, String> eObjectsPseudoIDs, List<EObject> eObjects, Map<Object, Object> exportOptions)
			throws EMFExportException {

		resetStopwatch();

		logger.info("Starting populating matrices with data");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		for (EObject eObject : eObjects) {
			// @formatter:off
			populateMatrixWithDataForEObjectWithEReferences(
					mapOfMatrices, 
					processedEObjectsIdentifiers, 
					eObjectsPseudoIDs,
					exportOptions,
					eObject);
			// @formatter:on
		}

		logger.info("Finished populating matrices with data in {} second(s)", elapsedTimeInSeconds());
	}

	private void populateMatrixWithDataForEObjectWithEReferences(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices, Set<String> eObjectsIdentifiers,
			Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions, EObject eObject)
			throws EMFExportException {

		// @formatter:off
		populateMatrixWithData(
				mapOfMatrices,
				eObjectsIdentifiers,
				eObjectsPseudoIDs,
				exportOptions,
				eObject);
		// @formatter:on

		eObject.eClass().getEAllReferences().stream().forEach(r -> {
			try {

				// @formatter:off
				populateMatrixWithDataForEReference(
						mapOfMatrices,
						eObjectsIdentifiers,
						eObjectsPseudoIDs,
						exportOptions,
						eObject,
						r);
				// @formatter:on

			} catch (EMFExportException e) {
				e.printStackTrace();
			}
		});
	}

	private void populateMatrixWithData(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<String> eObjectsIdentifiers, Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions,
			EObject... eObjects) throws EMFExportException {
		if ((eObjects.length > 0) && !isProcessed(eObjectsIdentifiers, eObjects[0])) {
			EClass eClass = eObjects[0].eClass();

			Table<Integer, Character, Object> matrix = getMatrix(mapOfMatrices, eClass);

			for (EObject eObject : eObjects) {
				eObjectsIdentifiers.add(getEObjectIdentifier(eObject));

				// @formatter:off
				populateMatrixWithData(
						mapOfMatrices,
						matrix,
						eObject,
						eObjectsPseudoIDs,
						exportOptions);
				// @formatter:on

				eObject.eClass().getEAllReferences().stream().forEach(r -> {
					try {

						// @formatter:off
						populateMatrixWithDataForEReference(
								mapOfMatrices,
								eObjectsIdentifiers,
								eObjectsPseudoIDs,
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
	private void populateMatrixWithDataForEReference(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<String> eObjectsIdentifiers, Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions,
			EObject eObject, EReference r) throws EMFExportException {
		if (!exportNonContainmentEnabled(exportOptions) && !r.isContainment()) {
			return;
		}

		Object value = eObject.eGet(r);

		if (value != null) {
			if (!r.isMany() && value instanceof EObject) {

				// @formatter:off
				populateMatrixWithData(
						mapOfMatrices,
						eObjectsIdentifiers,
						eObjectsPseudoIDs,
						exportOptions,
						(EObject) value);
				// @formatter:on

			} else if (r.isMany()) {

				// @formatter:off
				populateMatrixWithData(
						mapOfMatrices,
						eObjectsIdentifiers,
						eObjectsPseudoIDs,
						exportOptions,
						((List<EObject>) value).toArray(EObject[]::new));
				// @formatter:on
			}
		}
	}

	private void populateMatrixWithData(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Table<Integer, Character, Object> matrix, EObject eObject, Map<String, String> eObjectsPseudoIDs,
			Map<Object, Object> exportOptions) throws EMFExportException {

		String matrixName = constructEClassMatrixName(eObject.eClass());

		logger.debug("Creating data for matrix named '{}'", matrixName);

		List<EStructuralFeature> eAllStructuralFeatures = eObject.eClass().getEAllStructuralFeatures();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		boolean hasIDOrPseudoID = hasIDOrPseudoID(eObject, eObjectsPseudoIDs);

		if (hasIDOrPseudoID) {
			// @formatter:off
			setIDValueCell(
					matrix, 
					rowIndex, 
					0,
					getIDOrPseudoID(eObject, eObjectsPseudoIDs));
			// @formatter:on
		}

		Iterator<EStructuralFeature> eAllStructuralFeaturesIt = eAllStructuralFeatures.iterator();

		int colIndex = 0;

		while (eAllStructuralFeaturesIt.hasNext()) {
			EStructuralFeature eStructuralFeature = eAllStructuralFeaturesIt.next();

			if ((eStructuralFeature.getName()).equalsIgnoreCase(ID_ATTRIBUTE_NAME)) {
				continue;
			}

			// @formatter:off
			populateMatrixCellWithData(
					mapOfMatrices,
					matrix,
					rowIndex, 
					(hasIDOrPseudoID ? (colIndex + 1) : colIndex),
					eObject,
					eStructuralFeature, 
					eObjectsPseudoIDs,
					exportOptions);
			// @formatter:on

			colIndex++;
		}
	}

	private void setIDValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex, String value) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), new EMFExportEObjectIDValueCell(value));
	}

	private Table<Integer, Character, Object> getMatrix(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			EClass eClass) throws EMFExportException {
		String matrixName = constructEClassMatrixName(eClass);

		if (!mapOfMatrices.containsKey(matrixName)) {
			throw new EMFExportException("Matrix '" + matrixName + "' does not exist!");
		}

		return mapOfMatrices.get(matrixName);
	}

	private void populateMatrixCellWithData(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Table<Integer, Character, Object> matrix, int rowIndex, int colIndex, EObject eObject,
			EStructuralFeature eStructuralFeature, Map<String, String> eObjectsPseudoIDs,
			Map<Object, Object> exportOptions) throws EMFExportException {

		if (eStructuralFeature instanceof EAttribute) {
			EAttribute eAttribute = (EAttribute) eStructuralFeature;

			Object value = eObject.eGet(eAttribute);
			if (value != null) {
				if (!eAttribute.isMany()) {

					if (value instanceof Date) {
						setDateValueCell(matrix, rowIndex, colIndex, (Date) value);

					} else if (value instanceof Number) {
						setNumberValueCell(matrix, rowIndex, colIndex, (Number) value);

					} else if (value instanceof Boolean) {
						setBooleanValueCell(matrix, rowIndex, colIndex, (Boolean) value);

					} else if (value instanceof byte[]) {
						// TODO: clarify how byte arrays should be handled
						setStringValueCell(matrix, rowIndex, colIndex, "EAttribute: byte[]");

					} else {
						setStringValueCell(matrix, rowIndex, colIndex,
								EcoreUtil.convertToString(eAttribute.getEAttributeType(), value));
					}

				} else {
					setMultiValueCell(matrix, rowIndex, colIndex, eAttribute, value);
				}

			} else {
				setVoidValueCell(matrix, rowIndex, colIndex);
			}

		} else if (eStructuralFeature instanceof EReference) {
			EReference eReference = (EReference) eStructuralFeature;

			// @formatter:off
			setEReferenceValueCell(
					mapOfMatrices,
					matrix, 
					rowIndex, 
					colIndex, 
					eObject, 
					eReference, 
					eObjectsPseudoIDs,
					exportOptions);
			// @formatter:on
		}
	}

	private void setStringValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			String value) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), value);
	}

	private void setDateValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex, Date value) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), value);
	}

	private void setNumberValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			Number value) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), value.floatValue());
	}

	private void setBooleanValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			Boolean value) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), value.booleanValue());
	}

	@SuppressWarnings("unchecked")
	private void setMultiValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
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

	private void setVoidValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), Optional.empty());
	}

	@SuppressWarnings("unchecked")
	private void setEReferenceValueCell(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Table<Integer, Character, Object> matrix, int rowIndex, int colIndex, EObject eObject, EReference r,
			Map<String, String> eObjectsPseudoIDs, Map<Object, Object> exportOptions) throws EMFExportException {
		Object value = eObject.eGet(r);

		if ((value == null) || (!r.isMany() && !(value instanceof EObject))
				|| (r.isMany() && ((List<EObject>) value).isEmpty())) {
			setVoidValueCell(matrix, rowIndex, colIndex);
		}

		if ((!r.isMany() && value instanceof EObject)
				|| (r.isMany() && !((List<EObject>) value).isEmpty() && (((List<EObject>) value).size() == 1))) {

			// @formatter:off
			setOneEReferenceValueCell(
					matrix, 
					rowIndex,
					colIndex,
					(!r.isMany() ? ((EObject) value) : ((List<EObject>) value).get(0)), 
					eObjectsPseudoIDs);
			// @formatter:on

		} else if (r.isMany() && !((List<EObject>) value).isEmpty() && (((List<EObject>) value).size() > 1)) {

			if (addMappingTableEnabled(exportOptions)) {

				// @formatter:off
				populateEReferencesMappingMatrixWithData(
						mapOfMatrices,
						matrix,
						rowIndex, 
						colIndex,
						eObject, 
						r, 
						((List<EObject>) value),
						eObjectsPseudoIDs);
				// @formatter:on

			} else {

				// @formatter:off
				setManyEReferencesValueCell(
						matrix, 
						rowIndex,
						colIndex, 
						((List<EObject>) value), 
						eObjectsPseudoIDs);
				// @formatter:on
			}
		}
	}

	private void setOneEReferenceValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EObject eObject, Map<String, String> eObjectsPseudoIDs) {

		if (hasID(eObject)) {

			// @formatter:off
			setIDEReferenceValueCell(
					matrix, 
					rowIndex,
					colIndex, 
					getID(eObject));
			// @formatter:on

		} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {

			// @formatter:off
			setIDEReferenceValueCell(
					matrix, 
					rowIndex,
					colIndex, 
					getPseudoID(eObject, eObjectsPseudoIDs));
			// @formatter:on

		} else {

			// @formatter:off
			setNoIDEReferenceValueCell(
					matrix, 
					rowIndex,
					colIndex, 
					eObject);
			// @formatter:on
		}
	}

	private void setManyEReferencesValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			List<EObject> eObjects, Map<String, String> eObjectsPseudoIDs) {

		List<String> values = new ArrayList<String>();

		for (int i = 0; i < eObjects.size(); i++) {
			EObject eObject = eObjects.get(i);

			if (hasID(eObject)) {
				values.add(getID(eObject));
			} else if (hasPseudoID(eObject, eObjectsPseudoIDs)) {
				values.add(getPseudoID(eObject, eObjectsPseudoIDs));
			}
		}

		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex),
				new EMFExportEObjectManyReferencesValueCell(values));
	}

	private void setIDEReferenceValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			String refId) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex),
				new EMFExportEObjectOneReferenceValueCell(refId));
	}

	private void setNoIDEReferenceValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EObject eObject) {

		setStringValueCell(matrix, rowIndex, colIndex, "EReference: " + eObject.eClass().getName());
	}

	private void populateEReferencesMappingMatrixWithData(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Table<Integer, Character, Object> matrix, int rowIndex, int colIndex, EObject fromEObject,
			EReference toEReference, List<EObject> toEObjects, Map<String, String> eObjectsPseudoIDs)
			throws EMFExportException {

		String eReferencesMappingMatrixName = constructEReferencesMappingMatrixName(fromEObject.eClass(),
				toEReference.getName());

		if (mapOfMatrices.containsKey(eReferencesMappingMatrixName)) {
			Table<Integer, Character, Object> eReferencesMappingMatrix = mapOfMatrices
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
					eObjectsPseudoIDs);
			// @formatter:on

		} else {
			throw new EMFExportException("No mapping matrix named '" + eReferencesMappingMatrixName + "'");
		}
	}

	private void setReferenceToEReferencesMappingMatrix(Table<Integer, Character, Object> matrix, int rowIndex,
			int colIndex, String eReferencesMappingMatrixName) {

		// @formatter:off
		setMappingMatrixReferenceValueCell(
				matrix, 
				rowIndex, 
				colIndex,
				eReferencesMappingMatrixName);
		// @formatter:on
	}

	private void setMappingMatrixReferenceValueCell(Table<Integer, Character, Object> matrix, int rowIndex,
			int colIndex, String eReferencesMappingMatrixName) {
		matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex),
				new EMFExportMappingMatrixReferenceValueCell(eReferencesMappingMatrixName,
						constructEReferencesMappingMatrixEReferenceValue(eReferencesMappingMatrixName)));
	}

	private String constructEReferencesMappingMatrixEReferenceValue(String eReferencesMappingMatrixName) {
		StringBuilder sb = new StringBuilder(100);
		sb.append("See: ");
		sb.append(eReferencesMappingMatrixName);
		return sb.toString();
	}

	private void populateEReferencesMappingMatrixWithData(Table<Integer, Character, Object> eReferencesMappingMatrix,
			EObject fromEObject, List<EObject> toEObjects, Map<String, String> eObjectsPseudoIDs) {

		for (EObject toEObject : toEObjects) {

			// @formatter:off
			populateEReferencesMappingMatrixRowWithData(
					eReferencesMappingMatrix, 
					fromEObject, 
					toEObject, 
					eObjectsPseudoIDs);
			// @formatter:on
		}
	}

	private void populateEReferencesMappingMatrixRowWithData(Table<Integer, Character, Object> eReferencesMappingMatrix,
			EObject fromEObject, EObject toEObject, Map<String, String> eObjectsPseudoIDs) {

		int rowsCount = eReferencesMappingMatrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		setOneEReferenceValueCell(eReferencesMappingMatrix, rowIndex, 0, fromEObject, eObjectsPseudoIDs);

		setOneEReferenceValueCell(eReferencesMappingMatrix, rowIndex, 1, toEObject, eObjectsPseudoIDs);
	}

	private void constructMetadataMatrixIfNotExists(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			EClass eClass) {

		String eClassMetadataMatrixName = constructEClassMetadataMatrixName(eClass);

		if (!mapOfMatrices.containsKey(eClassMetadataMatrixName)) {
			logger.debug("Creating metadata matrix named '{}'", eClassMetadataMatrixName);

			Table<Integer, Character, Object> eClassMetadataMatrix = HashBasedTable.create();

			maybeSetEClassMetadataDocumentation(eClassMetadataMatrix, eClass);

			constructEClassMetadataMatrixColumnHeaders(eClassMetadataMatrix);

			mapOfMatrices.put(eClassMetadataMatrixName, eClassMetadataMatrix);
		}
	}

	private void constructMetadataMatrixIfNotExists(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			EEnum eEnum) {

		String eEnumMetadataMatrixName = constructEEnumMetadataMatrixName(eEnum);

		if (!mapOfMatrices.containsKey(eEnumMetadataMatrixName)) {
			logger.debug("Creating metadata matrix named '{}'", eEnumMetadataMatrixName);

			Table<Integer, Character, Object> eEnumMetadataMatrix = HashBasedTable.create();

			maybeSetEEnumMetadataDocumentation(eEnumMetadataMatrix, eEnum);

			constructEEnumMetadataMatrixColumnHeaders(eEnumMetadataMatrix);

			mapOfMatrices.put(eEnumMetadataMatrixName, eEnumMetadataMatrix);
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

	private void populateMatricesWithMetadata(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<EClass> eClasses, Set<EEnum> eEnums) throws EMFExportException {

		resetStopwatch();

		logger.info("Starting populating matrices with metadata");

		populateMatricesWithEClassesMetadata(mapOfMatrices, eClasses);
		populateMatricesWithEEnumsMetadata(mapOfMatrices, eEnums);

		logger.info("Finished populating matrices with metadata in {} second(s)", elapsedTimeInSeconds());
	}

	private void populateMatricesWithEClassesMetadata(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<EClass> eClasses) throws EMFExportException {

		for (EClass eClass : eClasses) {
			String eClassMetadataMatrixName = constructEClassMetadataMatrixName(eClass);

			if (mapOfMatrices.containsKey(eClassMetadataMatrixName)) {
				Table<Integer, Character, Object> matrix = mapOfMatrices.get(eClassMetadataMatrixName);

				populateMatrixWithEClassMetadata(matrix, eClass);

			} else {
				throw new EMFExportException("No metadata matrix for EClass named '" + eClassMetadataMatrixName + "'");
			}
		}
	}

	private void populateMatricesWithEEnumsMetadata(Map<String, Table<Integer, Character, Object>> mapOfMatrices,
			Set<EEnum> eEnums) throws EMFExportException {

		for (EEnum eEnum : eEnums) {
			String eEnumMetadataMatrixName = constructEEnumMetadataMatrixName(eEnum);

			if (mapOfMatrices.containsKey(eEnumMetadataMatrixName)) {
				Table<Integer, Character, Object> matrix = mapOfMatrices.get(eEnumMetadataMatrixName);

				populateMatrixWithEEnumMetadata(matrix, eEnum);

			} else {
				throw new EMFExportException("No metadata matrix for EEnum named '" + eEnumMetadataMatrixName + "'");
			}
		}
	}

	private void maybeSetEClassMetadataDocumentation(Table<Integer, Character, Object> matrix, EClass eClass) {
		EAnnotation genModelAnnotation = eClass.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(matrix, genModelAnnotation);
		}
	}

	private void maybeSetEEnumMetadataDocumentation(Table<Integer, Character, Object> matrix, EEnum eEnum) {
		EAnnotation genModelAnnotation = eEnum.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);
		if (genModelAnnotation != null) {
			setTypeLevelMetadataDocumentation(matrix, genModelAnnotation);
		}
	}

	private void setTypeLevelMetadataDocumentation(Table<Integer, Character, Object> matrix,
			EAnnotation genModelAnnotation) {
		Map<String, String> genModelAnnotationDetails = genModelAnnotation.getDetails().map();

		if (genModelAnnotationDetails.containsKey(DOCUMENTATION_GENMODEL_DETAILS)) {

			matrix.put(Integer.valueOf(1), getMatrixColumnName(0), METADATA_DOCUMENTATION_HEADER);

			matrix.put(Integer.valueOf(1), getMatrixColumnName(1),
					genModelAnnotationDetails.get(DOCUMENTATION_GENMODEL_DETAILS));
		}
	}

	private void constructEClassMetadataMatrixColumnHeaders(Table<Integer, Character, Object> matrix) {
		constructMetadataMatrixColumnHeaders(matrix, METADATA_ECLASS_MATRIX_COLUMNS_HEADERS);
	}

	private void constructEEnumMetadataMatrixColumnHeaders(Table<Integer, Character, Object> matrix) {
		constructMetadataMatrixColumnHeaders(matrix, METADATA_EENUM_MATRIX_COLUMNS_HEADERS);
	}

	private void constructMetadataMatrixColumnHeaders(Table<Integer, Character, Object> matrix, List<String> headers) {
		int columnsCount = headers.size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			matrix.put(Integer.valueOf(rowIndex), getMatrixColumnName(colIndex), headers.get(colIndex));
		}
	}

	private void populateMatrixWithEClassMetadata(Table<Integer, Character, Object> matrix, EClass eClass) {
		eClass.getEAllStructuralFeatures().forEach(eStructuralFeature -> {
			populateEClassMetadataMatrixRowWithData(matrix, eStructuralFeature);
		});
	}

	private void populateMatrixWithEEnumMetadata(Table<Integer, Character, Object> matrix, EEnum eEnum) {
		eEnum.getELiterals().forEach(eEnumLiteral -> {
			populateEEnumMetadataMatrixRowWithData(matrix, eEnumLiteral);
		});
	}

	private void populateEClassMetadataMatrixRowWithData(Table<Integer, Character, Object> matrix,
			EStructuralFeature eStructuralFeature) {

		int columnsCount = matrix.columnKeySet().size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			populateEClassMetadataCellWithData(matrix, rowIndex, colIndex, eStructuralFeature);
		}
	}

	private void populateEEnumMetadataMatrixRowWithData(Table<Integer, Character, Object> matrix,
			EEnumLiteral eEnumLiteral) {

		int columnsCount = matrix.columnKeySet().size();

		int rowsCount = matrix.rowKeySet().size();

		int rowIndex = (rowsCount + 1);

		for (int colIndex = 0; colIndex < columnsCount; colIndex++) {
			populateEEnumMetadataCellWithData(matrix, rowIndex, colIndex, eEnumLiteral);
		}
	}

	private void populateEClassMetadataCellWithData(Table<Integer, Character, Object> matrix, int rowIndex,
			int colIndex, EStructuralFeature eStructuralFeature) {
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

	private void populateEEnumMetadataCellWithData(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
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

	private void setEClassMetadataNameValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setStringValueCell(matrix, rowIndex, colIndex, eStructuralFeature.getName());
	}

	private void setEClassMetadataTypeValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
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

	private void setEClassMetadataIsManyValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(matrix, rowIndex, colIndex, eStructuralFeature.isMany());
	}

	private void setEClassMetadataIsRequiredValueCell(Table<Integer, Character, Object> matrix, int rowIndex,
			int colIndex, EStructuralFeature eStructuralFeature) {
		setBooleanValueCell(matrix, rowIndex, colIndex, eStructuralFeature.isRequired());
	}

	private void setEClassMetadataDefaultValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
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

	private void setEStructuralFeatureMetadataDocumentationValueCell(Table<Integer, Character, Object> matrix,
			int rowIndex, int colIndex, EStructuralFeature eStructuralFeature) {
		EAnnotation genModelAnnotation = eStructuralFeature.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(matrix, rowIndex, colIndex, genModelAnnotation);
	}

	private void setEEnumMetadataNameValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setStringValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getName());
	}

	private void setEEnumMetadataLiteralValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setStringValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getLiteral());
	}

	private void setEEnumMetadataValueValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			EEnumLiteral eEnumLiteral) {
		setNumberValueCell(matrix, rowIndex, colIndex, eEnumLiteral.getValue());
	}

	private void setEEnumLiteralMetadataDocumentationValueCell(Table<Integer, Character, Object> matrix, int rowIndex,
			int colIndex, EEnumLiteral eEnumLiteral) {
		EAnnotation genModelAnnotation = eEnumLiteral.getEAnnotation(DOCUMENTATION_GENMODEL_SOURCE);

		setMetadataDocumentationValueCell(matrix, rowIndex, colIndex, genModelAnnotation);
	}

	private void setMetadataDocumentationValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
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

	private void setMetadataDocumentationValueCell(Table<Integer, Character, Object> matrix, int rowIndex, int colIndex,
			String documentation) {
		setStringValueCell(matrix, rowIndex, colIndex, documentation);
	}

	private void generatePseudoIDs(List<EObject> eObjects, Map<String, String> eObjectsPseudoIDs) {
		resetStopwatch();

		logger.info("Starting generation of pseudo IDs");

		final Set<String> processedEObjectsIdentifiers = new HashSet<String>();

		generatePseudoIDs(eObjects, processedEObjectsIdentifiers, eObjectsPseudoIDs);

		logger.info("Finished generation of pseudo IDs in {} second(s)", elapsedTimeInSeconds());
	}

	private void generatePseudoIDs(List<EObject> eObjects, Set<String> processedEObjectsIdentifiers,
			Map<String, String> eObjectsPseudoIDs) {
		for (EObject eObject : eObjects) {
			if (isProcessed(processedEObjectsIdentifiers, eObject)) {
				continue;
			}

			processedEObjectsIdentifiers.add(getEObjectIdentifier(eObject));

			generatePseudoID(eObject, eObjectsPseudoIDs);

			eObject.eClass().getEAllReferences().stream().forEach(eReference -> {
				generatePseudoID(eObject, eReference, processedEObjectsIdentifiers, eObjectsPseudoIDs);
			});
		}
	}

	@SuppressWarnings("unchecked")
	private void generatePseudoID(EObject eObject, EReference eReference, Set<String> processedEObjectsIdentifiers,
			Map<String, String> eObjectsPseudoIDs) {
		Object value = eObject.eGet(eReference);
		if (value != null) {
			if (!eReference.isMany() && value instanceof EObject) {
				generatePseudoID((EObject) value, eObjectsPseudoIDs);
			} else if (eReference.isMany()) {
				generatePseudoIDs((List<EObject>) value, processedEObjectsIdentifiers, eObjectsPseudoIDs);
			}
		}
	}

	private void generatePseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		if (!hasIDOrPseudoID(eObject, eObjectsPseudoIDs)) {
			logger.debug("Generating pseudo ID for EObject ID '{}' named '{}'", getEObjectIdentifier(eObject),
					eObject.eClass().getName());
			eObjectsPseudoIDs.put(getEObjectIdentifier(eObject), UUID.randomUUID().toString());
		}
	}

	private String getEObjectIdentifier(EObject eObject) {
		return EcoreUtil.getIdentification(eObject);
	}

	protected boolean hasIDOrPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return (hasID(eObject) || hasPseudoID(eObject, eObjectsPseudoIDs));
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

	protected boolean hasPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return (eObjectsPseudoIDs.containsKey(getEObjectIdentifier(eObject)));
	}

	protected String getPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		return eObjectsPseudoIDs.get(getEObjectIdentifier(eObject));
	}

	protected String getIDOrPseudoID(EObject eObject, Map<String, String> eObjectsPseudoIDs) {
		String id = getID(eObject);
		if (id != null) {
			return id;
		}

		return getPseudoID(eObject, eObjectsPseudoIDs);
	}

	private String constructMatrixColumnHeaderName(EStructuralFeature eStructuralFeature) {
		StringBuilder sb = new StringBuilder(100);
		if (eStructuralFeature instanceof EReference) {
			sb.append(REF_COLUMN_PREFIX);
		}
		sb.append(eStructuralFeature.getName());
		return sb.toString();
	}

	// TODO: handle cases where there are more than 26 columns
	protected Character getMatrixColumnName(int colIndex) {
		return Character.valueOf(MATRIX_COLUMNS_HEADERS[colIndex]);
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

	protected Map<String, Table<Integer, Character, Object>> eObjectMatricesOnly(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices) {
		// @formatter:off
		return mapOfMatrices.entrySet().stream()
				.filter(entry -> (!isMetadataMatrix(entry.getKey()) && !isMappingMatrix(entry.getKey())))
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		// @formatter:on
	}

	protected Map<String, Table<Integer, Character, Object>> metadataMatricesOnly(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices) {
		// @formatter:off
		return mapOfMatrices.entrySet().stream()
				.filter(entry -> isMetadataMatrix(entry.getKey()))
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		// @formatter:on
	}

	protected Map<String, Table<Integer, Character, Object>> mappingMatricesOnly(
			Map<String, Table<Integer, Character, Object>> mapOfMatrices) {
		// @formatter:off
		return mapOfMatrices.entrySet().stream()
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

	protected Map<Object, Object> validateExportOptions(Map<?, ?> options) throws EMFExportException {
		if (options == null) {
			throw new EMFExportException("Please specify export options!");
		}

		Map<Object, Object> exportOptions = Map.copyOf(options);

		return exportOptions;
	}

	protected Locale locale(Map<Object, Object> exportOptions) {
		return ((Locale) exportOptions.getOrDefault(EMFExportOptions.OPTION_LOCALE, Locale.getDefault()));
	}

	protected boolean exportNonContainmentEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, Boolean.FALSE));
	}

	protected boolean exportMetadataEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_EXPORT_METADATA, Boolean.FALSE));
	}

	protected boolean addMappingTableEnabled(Map<Object, Object> exportOptions) {
		return ((boolean) exportOptions.getOrDefault(EMFExportOptions.OPTION_ADD_MAPPING_TABLE, Boolean.FALSE));
	}

	protected void resetStopwatch() {
		this.stopwatch.reset().start();
	}

	protected double elapsedTimeInSeconds() {
		return (double) this.stopwatch.elapsed().toNanos() / 1000000000.0;
	}
}
