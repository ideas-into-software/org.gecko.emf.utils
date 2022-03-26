/**
 */
package org.gecko.emf.collection;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>EContainment Collection</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.collection.EContainmentCollection#getValues <em>Values</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.collection.CollectionPackage#getEContainmentCollection()
 * @model
 * @generated
 */
public interface EContainmentCollection extends ECollection {
	/**
	 * Returns the value of the '<em><b>Values</b></em>' containment reference list.
	 * The list contents are of type {@link org.eclipse.emf.ecore.EObject}.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Values</em>' containment reference list.
	 * @see org.gecko.emf.collection.CollectionPackage#getEContainmentCollection_Values()
	 * @model containment="true"
	 * @generated
	 */
	EList<EObject> getValues();

} // EContainmentCollection
