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

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Converter</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.utilities.Converter#getConverterId <em>Converter Id</em>}</li>
 *   <li>{@link org.gecko.emf.utilities.Converter#getFromType <em>From Type</em>}</li>
 *   <li>{@link org.gecko.emf.utilities.Converter#getToType <em>To Type</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.utilities.UtilitiesPackage#getConverter()
 * @model
 * @generated
 */
public interface Converter extends EObject {
	/**
	 * Returns the value of the '<em><b>Converter Id</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Converter Id</em>' attribute.
	 * @see #setConverterId(String)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getConverter_ConverterId()
	 * @model id="true" required="true"
	 * @generated
	 */
	String getConverterId();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.Converter#getConverterId <em>Converter Id</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Converter Id</em>' attribute.
	 * @see #getConverterId()
	 * @generated
	 */
	void setConverterId(String value);

	/**
	 * Returns the value of the '<em><b>From Type</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>From Type</em>' reference.
	 * @see #setFromType(EClassifier)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getConverter_FromType()
	 * @model
	 * @generated
	 */
	EClassifier getFromType();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.Converter#getFromType <em>From Type</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>From Type</em>' reference.
	 * @see #getFromType()
	 * @generated
	 */
	void setFromType(EClassifier value);

	/**
	 * Returns the value of the '<em><b>To Type</b></em>' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>To Type</em>' reference.
	 * @see #setToType(EClassifier)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getConverter_ToType()
	 * @model
	 * @generated
	 */
	EClassifier getToType();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.Converter#getToType <em>To Type</em>}' reference.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>To Type</em>' reference.
	 * @see #getToType()
	 * @generated
	 */
	void setToType(EClassifier value);

} // Converter
