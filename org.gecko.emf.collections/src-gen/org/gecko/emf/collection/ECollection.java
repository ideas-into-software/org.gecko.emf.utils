/**
 */
package org.gecko.emf.collection;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>ECollection</b></em>'.
 * <!-- end-user-doc -->
 *
 *
 * @see org.gecko.emf.collection.CollectionPackage#getECollection()
 * @model interface="true" abstract="true"
 * @generated
 */
public interface ECollection extends EObject {
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @model kind="operation" dataType="org.gecko.emf.collection.EList" unique="false"
	 * @generated
	 */
	EList<EObject> getValues();

} // ECollection
