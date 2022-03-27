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
package org.gecko.emf.collection;

import org.eclipse.emf.ecore.EFactory;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see org.gecko.emf.collection.CollectionPackage
 * @generated
 */
public interface CollectionFactory extends EFactory {
	/**
	 * The singleton instance of the factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @generated
	 */
	CollectionFactory eINSTANCE = org.gecko.emf.collection.impl.CollectionFactoryImpl.init();

	/**
	 * Returns a new object of class '<em>EContainment Collection</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>EContainment Collection</em>'.
	 * @generated
	 */
	EContainmentCollection createEContainmentCollection();

	/**
	 * Returns a new object of class '<em>EReference Collection</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>EReference Collection</em>'.
	 * @generated
	 */
	EReferenceCollection createEReferenceCollection();

	/**
	 * Returns a new object of class '<em>EIterable</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>EIterable</em>'.
	 * @generated
	 */
	EIterable createEIterable();

	/**
	 * Returns a new object of class '<em>Feature Path</em>'.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return a new object of class '<em>Feature Path</em>'.
	 * @generated
	 */
	FeaturePath createFeaturePath();

	/**
	 * Returns the package supported by this factory.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the package supported by this factory.
	 * @generated
	 */
	CollectionPackage getCollectionPackage();

} //CollectionFactory
