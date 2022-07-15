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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.gecko.emf.json.configuration.ConfigurableJsonResource;
import org.gecko.emf.json.configuration.ConfigurableJsonResourceFactory;
import org.gecko.emf.json.constants.EMFJs;
import org.gecko.emf.osgi.example.model.basic.BasicFactory;
import org.gecko.emf.osgi.example.model.basic.BasicPackage;
import org.gecko.emf.osgi.example.model.basic.Person;
import org.gecko.emf.osgi.example.model.basic.impl.BasicPackageImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author mark
 * @since 15.07.2022
 */
public class RootElementTest {
	
	private ResourceSetImpl resourceSet;
	private BasicPackage packageImpl;
	private BasicFactory factoryImpl;

	@BeforeEach
	public void beforeEach() {
		resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
		packageImpl = BasicPackageImpl.init();
		factoryImpl = BasicFactory.eINSTANCE;
		resourceSet.getPackageRegistry().put(BasicPackage.eNS_URI, packageImpl);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("json", new ConfigurableJsonResourceFactory());
	}
	
	@Test
	public void testSaveJson() {
		Resource resource = resourceSet.createResource(URI.createURI("test.json"));
		assertNotNull(resource);
		assertTrue(resource instanceof ConfigurableJsonResource);
		
		Person p = factoryImpl.createPerson();
		p.setFirstName("Emil");
		p.setLastName("Tester");
		resource.getContents().add(p);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			resource.save(baos, null);
		} catch (IOException e) {
			fail("Error saving Person");
		}
		String result = new String(baos.toByteArray());
		System.out.println(result);
	}
	
	@Test
	public void testLoadJsonError() {
		String json = "{\n"
				+ "  \"firstName\" : \"Emil\",\n"
				+ "  \"lastName\" : \"Tester\"\n"
				+ "}";
		Resource loadResource = resourceSet.createResource(URI.createURI("test-load-error.json"));
		assertNotNull(loadResource);
		assertTrue(loadResource instanceof ConfigurableJsonResource);
		
		ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes());
		try {
			loadResource.load(bais, null);
			assertEquals(0, loadResource.getContents().size());
		} catch (IOException e) {
			fail("Error loading Person");
		}
	}
	
	@Test
	public void testLoadJson() {
		String json = "{\n"
				+ "  \"firstName\" : \"Emil\",\n"
				+ "  \"lastName\" : \"Tester\"\n"
				+ "}";
		Resource loadResource = resourceSet.createResource(URI.createURI("test-load.json"));
		assertNotNull(loadResource);
		assertTrue(loadResource instanceof ConfigurableJsonResource);
		
		ByteArrayInputStream bais = new ByteArrayInputStream(json.getBytes());
		try {
			Map<String, Object> loadOptions = new HashMap<String, Object>();
			loadOptions.put(EMFJs.OPTION_ROOT_ELEMENT, packageImpl.getPerson());
			loadResource.load(bais, loadOptions);
			assertEquals(1, loadResource.getContents().size());
			Person p = (Person) loadResource.getContents().get(0);
			assertEquals("Emil", p.getFirstName());
			assertEquals("Tester", p.getLastName());
		} catch (IOException e) {
			fail("Error loading Person");
		}
	}

}
