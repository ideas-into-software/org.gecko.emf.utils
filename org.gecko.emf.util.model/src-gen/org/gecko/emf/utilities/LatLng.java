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

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Lat Lng</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.utilities.LatLng#getLatitude <em>Latitude</em>}</li>
 *   <li>{@link org.gecko.emf.utilities.LatLng#getLongitude <em>Longitude</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.utilities.UtilitiesPackage#getLatLng()
 * @model
 * @generated
 */
public interface LatLng extends EObject {
	/**
	 * Returns the value of the '<em><b>Latitude</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Latitude</em>' attribute.
	 * @see #setLatitude(double)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getLatLng_Latitude()
	 * @model
	 * @generated
	 */
	double getLatitude();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.LatLng#getLatitude <em>Latitude</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Latitude</em>' attribute.
	 * @see #getLatitude()
	 * @generated
	 */
	void setLatitude(double value);

	/**
	 * Returns the value of the '<em><b>Longitude</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Longitude</em>' attribute.
	 * @see #setLongitude(double)
	 * @see org.gecko.emf.utilities.UtilitiesPackage#getLatLng_Longitude()
	 * @model
	 * @generated
	 */
	double getLongitude();

	/**
	 * Sets the value of the '{@link org.gecko.emf.utilities.LatLng#getLongitude <em>Longitude</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Longitude</em>' attribute.
	 * @see #getLongitude()
	 * @generated
	 */
	void setLongitude(double value);

} // LatLng
