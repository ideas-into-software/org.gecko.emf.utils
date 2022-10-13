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
package org.gecko.emf.json.tests;

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
public class EMFJsonResourceFactoryConfigTest {
	
	@Test 
	public void testConfigure(@InjectService(timeout = 2000, filter="(component.name=EMFJsonConfigurator)") ServiceAware<ResourceFactoryConfigurator> resFacConfAware) {

		assertThat(resFacConfAware).isNotNull();
		assertThat(resFacConfAware.getServiceReferences()).hasSize(1);
		ResourceFactoryConfigurator resFacConf = resFacConfAware.getService();
		Resource.Factory.Registry registry = Resource.Factory.Registry.INSTANCE;
		resFacConf.configureResourceFactory(registry);
		
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/yml"));
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/yaml"));
		assertThat(registry.getContentTypeToFactoryMap().containsKey("text/x-java-properties"));
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/json"));
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/x-json"));
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/emf-json"));
		
		assertThat(registry.getExtensionToFactoryMap().containsKey("json"));
		assertThat(registry.getExtensionToFactoryMap().containsKey("properties"));
		assertThat(registry.getExtensionToFactoryMap().containsKey("yml"));
		assertThat(registry.getExtensionToFactoryMap().containsKey("yaml"));
		
		assertThat(registry.getProtocolToFactoryMap().containsKey("json"));
		assertThat(registry.getProtocolToFactoryMap().containsKey("prop"));
	}
	
	@Test 
	public void testUnconfigure(@InjectService(timeout = 2000, filter="(component.name=EMFJsonConfigurator)") ServiceAware<ResourceFactoryConfigurator> resFacConfAware) {

		assertThat(resFacConfAware).isNotNull();
		assertThat(resFacConfAware.getServiceReferences()).hasSize(1);
		ResourceFactoryConfigurator resFacConf = resFacConfAware.getService();
		Resource.Factory.Registry registry = Resource.Factory.Registry.INSTANCE;
		resFacConf.configureResourceFactory(registry);
		resFacConf.unconfigureResourceFactory(registry);
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/yml")).isFalse();
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/yaml")).isFalse();
		assertThat(registry.getContentTypeToFactoryMap().containsKey("text/x-java-properties")).isFalse();
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/json")).isFalse();
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/x-json")).isFalse();
		assertThat(registry.getContentTypeToFactoryMap().containsKey("application/emf-json")).isFalse();
		
		assertThat(registry.getExtensionToFactoryMap().containsKey("json")).isFalse();
		assertThat(registry.getExtensionToFactoryMap().containsKey("properties")).isFalse();
		assertThat(registry.getExtensionToFactoryMap().containsKey("yml")).isFalse();
		assertThat(registry.getExtensionToFactoryMap().containsKey("yaml")).isFalse();
		
		assertThat(registry.getProtocolToFactoryMap().containsKey("json")).isFalse();
		assertThat(registry.getProtocolToFactoryMap().containsKey("prop")).isFalse();
	}
	
	

}
