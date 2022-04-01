/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 * 	Data In Motion - initial API and implementation
 */
package org.gecko.emf.utilities;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Filter</b></em>'.
 * <!-- end-user-doc -->
 *
 * <!-- begin-model-doc -->
 * The filter definition
 * <!-- end-model-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.utilities.Filter#getIndex <em>Index</em>}</li>
 *   <li>{@link org.gecko.emf.utilities.Filter#getField <em>Field</em>}</li>
 *   <li>{@link org.gecko.emf.utilities.Filter#getValue <em>Value</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.utilities.UtilitiesPackage#getFilter()
 * @model
 * @generated
 */
public interface Filter extends EObject {
	/**
	 * Returns the value of the '<em><b>Index</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * Index for the right ordering
	 * <!-- end-model-doc -->
	 * @return the value of the '<em>Index</em>' attribute.
	 * @see #setIndex(int)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getFilter_Index()
	 * @model required="true"
	 * @generated
	 */
	int getIndex();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.Filter#getIndex <em>Index</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Index</em>' attribute.
	 * @see #getIndex()
	 * @generated
	 */
	void setIndex(int value);

	/**
	 * Returns the value of the '<em><b>Field</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * The field name to sort against
	 * <!-- end-model-doc -->
	 * @return the value of the '<em>Field</em>' attribute.
	 * @see #setField(String)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getFilter_Field()
	 * @model required="true"
	 * @generated
	 */
	String getField();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.Filter#getField <em>Field</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Field</em>' attribute.
	 * @see #getField()
	 * @generated
	 */
	void setField(String value);

	/**
	 * Returns the value of the '<em><b>Value</b></em>' attribute list.
	 * The list contents are of type {@link java.lang.String}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * The values to filter against
	 * <!-- end-model-doc -->
	 * @return the value of the '<em>Value</em>' attribute list.
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getFilter_Value()
	 * @model required="true"
	 * @generated
	 */
	EList<String> getValue();

} // Filter
