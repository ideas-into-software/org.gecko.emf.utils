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

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Feature Path</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.collection.FeaturePath#getName <em>Name</em>}</li>
 *   <li>{@link org.gecko.emf.collection.FeaturePath#getFeature <em>Feature</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.collection.CollectionPackage#getFeaturePath()
 * @model
 * @generated
 */
public interface FeaturePath extends EObject {
	/**
	 * Returns the value of the '<em><b>Name</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Name</em>' attribute.
	 * @see #setName(String)
	 * @see org.gecko.emf.collection.CollectionPackage#getFeaturePath_Name()
	 * @model
	 * @generated
	 */
	String getName();

	/**
	 * Sets the value of the '{@link org.gecko.emf.collection.FeaturePath#getName <em>Name</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Name</em>' attribute.
	 * @see #getName()
	 * @generated
	 */
	void setName(String value);

	/**
	 * Returns the value of the '<em><b>Feature</b></em>' reference list.
	 * The list contents are of type {@link org.eclipse.emf.ecore.EStructuralFeature}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Feature</em>' reference list.
	 * @see org.gecko.emf.collection.CollectionPackage#getFeaturePath_Feature()
	 * @model
	 * @generated
	 */
	EList<EStructuralFeature> getFeature();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model objectRequired="true"
	 *        annotation="http://www.eclipse.org/emf/2002/GenModel body='&lt;%java.util.List%&gt;&lt;Object&gt; result = &lt;%org.gecko.emf.collection.helper.ECollectionsHelper%&gt;.getFeaturePathValue(this, object);\nif (result != null) {\n\treturn &lt;%org.eclipse.emf.common.util.ECollections%&gt;.asEList(result);\n}\nreturn &lt;%org.eclipse.emf.common.util.ECollections%&gt;.emptyEList();'"
	 * @generated
	 */
	EList<Object> getValue(EObject object);

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model objectRequired="true"
	 *        annotation="http://www.eclipse.org/emf/2002/GenModel body='if (object == null) {\n\treturn false;\n}\nreturn &lt;%org.gecko.emf.collection.helper.ECollectionsHelper%&gt;.validateFeaturePath(this, object.eClass());'"
	 * @generated
	 */
	boolean isValid(EObject object);

} // FeaturePath
