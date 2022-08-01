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
package org.gecko.emf.rest.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.jaxrs.annotations.AnnotationConverter;
import org.gecko.emf.jaxrs.annotations.json.RootElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;

/**
 * 
 * @author ilenia
 * @since Aug 1, 2022
 */
@RunWith(MockitoJUnitRunner.class)
public class EMFJsonAnnotationConverterRootElementInvalidTest {
	
	@InjectBundleContext
	BundleContext bundleContext;
	
	@Mock 
	ResourceSet resourceSetMock;
	
	@BeforeEach
	public void beforeEach() {
		MockitoAnnotations.openMocks(this);
		assertThat(resourceSetMock).isNotNull();
	
		Dictionary<String, Object> properties = new Hashtable<String, Object>();
		properties.put("service.ranking", Integer.MAX_VALUE);
		bundleContext.registerService(ResourceSet.class, new PrototypeServiceFactory<ResourceSet>() {

			@Override
			public ResourceSet getService(Bundle bundle, ServiceRegistration<ResourceSet> registration) {
				return resourceSetMock;
			}

			@Override
			public void ungetService(Bundle bundle, ServiceRegistration<ResourceSet> registration, ResourceSet service) {
				return;
			}
		}, properties);						
		Mockito.when(resourceSetMock.getEObject(Mockito.any(URI.class), Mockito.anyBoolean())).thenReturn(null);
	}
	
	@Test
	public void testRootElementAnnotationInvalid(@InjectService(cardinality = 0, timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		Annotation root = new RootElement() {
			
			@Override
			public Class<? extends Annotation> annotationType() {
				return null;
			}
			
			@Override
			public String rootClassUri() {
				return "http://datainmotion.com/emf/util/examplemodel/1.0#//Building";
			}
		};
		
		AnnotationConverter annotationConverter = annotationConverterAware.getService();

		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		assertThrows(IllegalArgumentException.class, new Executable() {

			@Override
			public void execute() throws Throwable {
				annotationConverter.convertAnnotation(root, false, options);					
			}
		});
	}


}
