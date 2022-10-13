/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.bson.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.ecore.resource.Resource;
import org.gecko.emf.osgi.ResourceFactoryConfigurator;
import org.junit.jupiter.api.Test;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;

/**
 * 
 * @author ilenia
 * @since Aug 2, 2022
 */
public class EMFBsonResourceFactoryConfigTest {
	
	@Test 
	public void testConfigure(@InjectService(timeout = 2000, filter="(component.name=EMFBsonConfigurator)") ServiceAware<ResourceFactoryConfigurator> resFacConfAware) {

		assertThat(resFacConfAware).isNotNull();
		assertThat(resFacConfAware.getServiceReferences()).hasSize(1);
		ResourceFactoryConfigurator resFacConf = resFacConfAware.getService();
		Resource.Factory.Registry registry = Resource.Factory.Registry.INSTANCE;
		resFacConf.configureResourceFactory(registry);
		
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/bson"));		
		assertThat(registry.getExtensionToFactoryMap().containsKey("bson"));		
		assertThat(registry.getProtocolToFactoryMap().containsKey("bson"));
	}
	
	@Test 
	public void testUnconfigure(@InjectService(timeout = 2000, filter="(component.name=EMFBsonConfigurator)") ServiceAware<ResourceFactoryConfigurator> resFacConfAware) {

		assertThat(resFacConfAware).isNotNull();
		assertThat(resFacConfAware.getServiceReferences()).hasSize(1);
		ResourceFactoryConfigurator resFacConf = resFacConfAware.getService();
		Resource.Factory.Registry registry = Resource.Factory.Registry.INSTANCE;
		resFacConf.configureResourceFactory(registry);
		resFacConf.unconfigureResourceFactory(registry);
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/bson")).isFalse();		
		assertThat(registry.getExtensionToFactoryMap().containsKey("bson")).isFalse();				
		assertThat(registry.getProtocolToFactoryMap().containsKey("bson")).isFalse();		
	}

}
