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
package org.gecko.emf.pushstream;

import org.eclipse.emf.ecore.EObject;

import org.osgi.util.pushstream.PushEventSource;
import org.osgi.util.pushstream.PushStreamProvider;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Custom Push Stream Provider</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link org.gecko.emf.pushstream.CustomPushStreamProvider#getEventSource <em>Event Source</em>}</li>
 *   <li>{@link org.gecko.emf.pushstream.CustomPushStreamProvider#getProvider <em>Provider</em>}</li>
 * </ul>
 *
 * @see org.gecko.emf.pushstream.PushstreamPackage#getCustomPushStreamProvider()
 * @model
 * @generated
 */
public interface CustomPushStreamProvider extends EPushStreamProvider {
	/**
	 * Returns the value of the '<em><b>Event Source</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Event Source</em>' attribute.
	 * @see #setEventSource(PushEventSource)
	 * @see org.gecko.emf.pushstream.PushstreamPackage#getCustomPushStreamProvider_EventSource()
	 * @model dataType="org.gecko.emf.pushstream.EPushEventSource" required="true" transient="true"
	 *        annotation="http://www.eclipse.org/emf/2002/GenModel"
	 * @generated
	 */
	PushEventSource<EObject> getEventSource();

	/**
	 * Sets the value of the '{@link org.gecko.emf.pushstream.CustomPushStreamProvider#getEventSource <em>Event Source</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Event Source</em>' attribute.
	 * @see #getEventSource()
	 * @generated
	 */
	void setEventSource(PushEventSource<EObject> value);

	/**
	 * Returns the value of the '<em><b>Provider</b></em>' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Provider</em>' attribute.
	 * @see #setProvider(PushStreamProvider)
	 * @see org.gecko.emf.pushstream.PushstreamPackage#getCustomPushStreamProvider_Provider()
	 * @model dataType="org.gecko.emf.pushstream.PushStreamProvider" required="true"
	 * @generated
	 */
	PushStreamProvider getProvider();

	/**
	 * Sets the value of the '{@link org.gecko.emf.pushstream.CustomPushStreamProvider#getProvider <em>Provider</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @param value the new value of the '<em>Provider</em>' attribute.
	 * @see #getProvider()
	 * @generated
	 */
	void setProvider(PushStreamProvider value);

} // CustomPushStreamProvider
