/**
 */
package org.gecko.emf.collection;

import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>EIterable</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.collection.EIterable#getDelegate <em>Delegate</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.collection.CollectionPackage#getEIterable()
 * @model superTypes="org.gecko.emf.collection.EIterableInterface"
 * @generated
 */
public interface EIterable extends EObject, Iterable<EObject> {
	/**
	 * Returns the value of the '<em><b>Delegate</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Delegate</em>' attribute.
	 * @see #setDelegate(Iterable)
	 * @see org.gecko.emf.collection.CollectionPackage#getEIterable_Delegate()
	 * @model dataType="org.gecko.emf.collection.Iterable"
	 * @generated
	 */
	Iterable<EObject> getDelegate();

	/**
	 * Sets the value of the '{@link org.gecko.emf.collection.EIterable#getDelegate <em>Delegate</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Delegate</em>' attribute.
	 * @see #getDelegate()
	 * @generated
	 */
	void setDelegate(Iterable<EObject> value);

} // EIterable
