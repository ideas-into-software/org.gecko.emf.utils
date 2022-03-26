/**
 */
package org.gecko.emf.pushstream;

import java.util.concurrent.BlockingQueue;

import org.eclipse.emf.ecore.EObject;

import org.osgi.util.pushstream.PushEvent;
import org.osgi.util.pushstream.PushStream;
import org.osgi.util.pushstream.PushStreamBuilder;
import org.osgi.util.pushstream.SimplePushEventSource;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>EPush Stream Provider</b></em>'.
 * <!-- end-user-doc -->
 *
 *
 * @see org.gecko.emf.pushstream.PushstreamPackage#getEPushStreamProvider()
 * @model interface="true" abstract="true"
 * @generated
 */
public interface EPushStreamProvider extends EObject {
	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * Creates a PushStream from the internal eventSource feature
	 * <!-- end-model-doc -->
	 * @model dataType="org.gecko.emf.pushstream.EPushStream" required="true"
	 * @generated
	 */
	PushStream<EObject> createPushStream();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * Creates a new SimplePushEventSource from the given PushStreamProvider
	 * <!-- end-model-doc -->
	 * @model dataType="org.gecko.emf.pushstream.ESimplePushEventSource" required="true"
	 * @generated
	 */
	SimplePushEventSource<EObject> createSimplePushEventSource();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * Creates a PushStream from the internal eventSource feature
	 * <!-- end-model-doc -->
	 * @model dataType="org.gecko.emf.pushstream.EPushStream" required="true"
	 * @generated
	 */
	PushStream<EObject> createPushStreamUnbuffered();

	/**
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * <!-- begin-model-doc -->
	 * Creates a PushStreamBuilder to customize the settings
	 * <!-- end-model-doc -->
	 * @model dataType="org.gecko.emf.pushstream.PushStreamBuilder"
	 * @generated
	 */
	PushStreamBuilder<EObject, BlockingQueue<PushEvent<? extends EObject>>> createPushStreamBuilder();

} // EPushStreamProvider
