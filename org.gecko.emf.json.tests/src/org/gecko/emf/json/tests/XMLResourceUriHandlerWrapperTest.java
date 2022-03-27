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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.xmi.XMLResource.URIHandler;
import org.gecko.emf.json.configuration.XMLResourceUriHandlerWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class XMLResourceUriHandlerWrapperTest {
	
	URIHandler handler;
	
	@BeforeEach
	private void setupServices() {
		handler = mock(URIHandler.class);
	}

	@Test
	public void testNullDelegate() {
		XMLResourceUriHandlerWrapper w = new XMLResourceUriHandlerWrapper(null);
		URI baseURI = URI.createURI("/base");
		URI theURI = URI.createURI("/uri");
		assertNull(w.resolve(baseURI, null));
		assertNull(w.deresolve(baseURI, null));
		assertNull(w.resolve(null, theURI));
		assertNull(w.deresolve(null, theURI));
		assertNull(w.resolve(baseURI, theURI));
		assertNull(w.deresolve(baseURI, theURI));
	}
	
	@Test
	public void testResolve() {
		XMLResourceUriHandlerWrapper w = new XMLResourceUriHandlerWrapper(handler);
		URI baseURI = URI.createURI("/base");
		URI theURI = URI.createURI("/uri");
		URI resolvedURI = URI.createURI("/resolved");
		when(handler.resolve(any(URI.class))).thenReturn(resolvedURI);
		
		assertNull(w.resolve(baseURI, null));
		assertEquals(resolvedURI, w.resolve(null, theURI));
		assertEquals(resolvedURI, w.resolve(baseURI, theURI));
		
		verify(handler, times(2)).resolve(any(URI.class));
		verify(handler, never()).deresolve(any(URI.class));
	}
	
	@Test
	public void testDeresolve() {
		XMLResourceUriHandlerWrapper w = new XMLResourceUriHandlerWrapper(handler);
		URI baseURI = URI.createURI("/base");
		URI theURI = URI.createURI("/uri");
		URI deresolvedURI = URI.createURI("/deresolved");
		when(handler.deresolve(any(URI.class))).thenReturn(deresolvedURI);
		
		assertNull(w.deresolve(baseURI, null));
		assertEquals(deresolvedURI, w.deresolve(null, theURI));
		assertEquals(deresolvedURI, w.deresolve(baseURI, theURI));
		
		verify(handler, times(2)).deresolve(any(URI.class));
		verify(handler, never()).resolve(any(URI.class));
	}

}
