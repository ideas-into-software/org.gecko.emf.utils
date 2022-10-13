package org.gecko.emf.rest.test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.emf.common.util.URI;
import org.gecko.emf.jaxrs.XMLURIHandler;
import org.junit.jupiter.api.Test;

/**
 * 
 * @author ilenia
 * @since Aug 2, 2022
 */
public class XMLURIHandlerTest {
	
	@Test
	public void testResolveURI() {
		
		System.out.println("I am running the Junit tests!!");
	
		URI uri = URI.createFileURI("/data/exampleDateFormat.json");
		XMLURIHandler handler = new XMLURIHandler(uri);
		URI resolvedUri = handler.resolve(uri);
		assertNotNull(uri);
		assertEquals(resolvedUri, uri);
		
		uri = URI.createFileURI("platform:/resource/org.gecko.emf.util.example.model/model/examplmodel.ecore");
		resolvedUri = handler.resolve(uri);
		assertNotNull(uri);
		assertNotEquals(resolvedUri, uri);
	}
	
	@Test
	public void testDeresolveURI() {
		
		URI uri = URI.createFileURI("platform:/resource/org.gecko.emf.util.example.model/model/examplmodel.ecore");
		XMLURIHandler handler = new XMLURIHandler(uri);
		assertNotNull(uri);	
		URI deserolvedURI = handler.deresolve(uri);
		assertEquals(deserolvedURI, uri);
		
		uri = URI.createFileURI("");
		deserolvedURI = handler.deresolve(uri);
		assertNull(deserolvedURI);
		
		uri = URI.createFileURI("/data/exampleDateFormat.json");
		deserolvedURI = handler.deresolve(uri);
		assertNotNull(deserolvedURI);
		
		uri = URI.createFileURI("/data/another/folder/exampleDateFormat.json");
		deserolvedURI = handler.deresolve(uri);
		assertNotNull(deserolvedURI);
		
	}

}
