/*
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
package org.gecko.emf.utilities.configuration;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.emf.ecore.EPackage;

import org.eclipse.emf.ecore.resource.Resource.Factory.Registry;

import org.gecko.emf.osgi.EMFNamespaces;
import org.gecko.emf.osgi.EPackageConfigurator;
import org.gecko.emf.osgi.ResourceFactoryConfigurator;

import org.gecko.emf.osgi.annotation.EMFModel;

import org.gecko.emf.osgi.annotation.provide.ProvideEMFModel;
import org.gecko.emf.osgi.annotation.provide.ProvideEMFResourceConfigurator;

import org.gecko.emf.osgi.annotation.require.RequireEMF;

import org.gecko.emf.utilities.UtilitiesPackage;

import org.gecko.emf.utilities.impl.UtilitiesPackageImpl;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

/**
 * <!-- begin-user-doc -->
 * The <b>EPackageConfiguration</b> and <b>ResourceFactoryConfigurator</b> for the model.
 * The package will be registered into a OSGi base model registry.
 * <!-- end-user-doc -->
 * @see EPackageConfigurator
 * @see ResourceFactoryConfigurator
 * @generated
 */
@Component(name="UtilitiesConfigurator", service= {EPackageConfigurator.class, ResourceFactoryConfigurator.class})
@EMFModel(name=UtilitiesPackage.eNAME, nsURI={UtilitiesPackage.eNS_URI}, version="1.0.0")
@RequireEMF
@ProvideEMFModel(name = UtilitiesPackage.eNAME, nsURI = { UtilitiesPackage.eNS_URI }, version = "1.0.0")
@ProvideEMFResourceConfigurator( name = UtilitiesPackage.eNAME,
	contentType = { "" }, 
	fileExtension = {
	"utilities"
 	},  
	version = "1.0.0"
)
public class UtilitiesConfigurationComponent implements EPackageConfigurator, ResourceFactoryConfigurator {
	private ServiceRegistration<?> packageRegistration = null;
	
	@Activate
	public void activate(BundleContext ctx) {
		UtilitiesPackage p = UtilitiesPackageImpl.init();
		if(p == null){
			p= UtilitiesPackageImpl.eINSTANCE;
			EPackage.Registry.INSTANCE.put(UtilitiesPackage.eNS_URI,p);
		}
		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		properties.put(EMFNamespaces.EMF_MODEL_NAME, UtilitiesPackage.eNAME);
		properties.put(EMFNamespaces.EMF_MODEL_NSURI, UtilitiesPackage.eNS_URI);
		properties.put(EMFNamespaces.EMF_MODEL_FILE_EXT, "utilities");
		String[] serviceClasses = new String[] {UtilitiesPackage.class.getName(), EPackage.class.getName()};
		packageRegistration = ctx.registerService(serviceClasses, p, properties);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.osgi.ResourceFactoryConfigurator#configureResourceFactory(org.eclipse.emf.ecore.resource.Resource.Factory.Registry)
	 * @generated
	 */
	@Override
	public void configureResourceFactory(Registry registry) {
		 
		 
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.osgi.ResourceFactoryConfigurator#unconfigureResourceFactory(org.eclipse.emf.ecore.resource.Resource.Factory.Registry)
	 * @generated
	 */
	@Override
	public void unconfigureResourceFactory(Registry registry) {
		 
		 
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.osgi.EPackageRegistryConfigurator#configureEPackage(org.eclipse.emf.ecore.EPackage.Registry)
	 * @generated
	 */
	@Override
	public void configureEPackage(org.eclipse.emf.ecore.EPackage.Registry registry) {
		registry.put(UtilitiesPackage.eNS_URI, UtilitiesPackageImpl.init());
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.osgi.EPackageRegistryConfigurator#unconfigureEPackage(org.eclipse.emf.ecore.EPackage.Registry)
	 * @generated
	 */
	@Override
	public void unconfigureEPackage(org.eclipse.emf.ecore.EPackage.Registry registry) {
		registry.remove(UtilitiesPackage.eNS_URI);
	}
	
	@Deactivate
	public void deactivate() {
		EPackage.Registry.INSTANCE.remove(UtilitiesPackage.eNS_URI);
		if(packageRegistration != null){
			packageRegistration.unregister();
		}
	}
}
