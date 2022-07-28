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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emfcloud.jackson.annotations.EcoreTypeInfo.USE;
import org.eclipse.emfcloud.jackson.databind.EMFContext;
import org.gecko.emf.jaxrs.annotations.AnnotationConverter;
import org.gecko.emf.jaxrs.annotations.json.EMFJSONConfig;
import org.gecko.emf.jaxrs.annotations.json.RootElement;
import org.gecko.emf.json.constants.EMFJs;
import org.gecko.emf.util.example.model.examplemodel.ExampleModelPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;

public class EMFJsonAnnotationConverterTest  {
	
	@InjectBundleContext
	BundleContext bundleContext;

	List<Method> methodList;


	@BeforeEach
	public void beforeEach(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		checkServices(examplePackageAware, annotationConverterAware);
		Method[] methods = EMFJsonAnnotationConverterTest.class.getDeclaredMethods();
		methodList = List.of(methods);
	}

	@Test
	public void testCanHandleRootElement(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "rootElementValid";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(RootElement.class);
		assertThat(annotationConverter.canHandle(annotation, false));
	}

	@Test
	public void testCanHandleEMFJsonConfig(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "emfJsonConfigDefault";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(EMFJSONConfig.class);
		assertThat(annotationConverter.canHandle(annotation, false));
	}

	@Test
	public void testCanHandleNotValidAnnotation(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "beforeEach";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(BeforeEach.class);
		assertThat(annotationConverter.canHandle(annotation, false)).isFalse();
	}


	@Test
	public void testRootElementAnnotationValid(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "rootElementValid";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(RootElement.class);
		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		annotationConverter.convertAnnotation(annotation, false, options);
		assertThat(options).isNotEmpty();
		assertThat(options.containsKey(EMFContext.Attributes.ROOT_ELEMENT));
		assertThat(options.get(EMFContext.Attributes.ROOT_ELEMENT)).isEqualTo(examplePackageAware.getService().getEClassifier("Building"));
	}

	@Disabled("This test requires mocking of static method URI.createURI(), which in turns requires as dependency mockito-inline which is not a bundle!")
	@Test
	public void testRootElementAnnotationInvalid(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		ResourceSet resourceSetMock = Mockito.mock(ResourceSet.class);
		assertThat(resourceSetMock).isNotNull();
		Mockito.when(resourceSetMock.getEObject(Mockito.any(URI.class), Mockito.anyBoolean())).thenReturn(null);
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
		MockedStatic<URI> uriMock = Mockito.mockStatic(URI.class);
		assertThat(uriMock).isNotNull();
		Mockito.when(URI.createURI(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);
		
		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "rootElementInvalid";
		Method method =  getMethod(methodName);

		Annotation annotation = method.getAnnotation(RootElement.class);
		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		assertThrows(IllegalArgumentException.class, new Executable() {

			@Override
			public void execute() throws Throwable {
				annotationConverter.convertAnnotation(annotation, false, options);					
			}
		});
	}

	@Test
	public void testEMFJsonConfigDefault(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "emfJsonConfigDefault";
		Method method =  getMethod(methodName);

		Annotation annotation = method.getAnnotation(EMFJSONConfig.class);
		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		annotationConverter.convertAnnotation(annotation, false, options);
		assertThat(options).isNotEmpty();

		assertThat(options.containsKey(EMFJs.OPTION_DATE_FORMAT));
		assertThat(options.get(EMFJs.OPTION_DATE_FORMAT)).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

		assertThat(options.containsKey(EMFJs.OPTION_INDENT_OUTPUT));
		assertThat(options.get(EMFJs.OPTION_INDENT_OUTPUT)).isEqualTo(true);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF)).isEqualTo(false);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE)).isEqualTo(false);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_TYPE));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_TYPE)).isEqualTo(true);

		assertThat(options.containsKey(EMFJs.OPTION_TYPE_USE));
		assertThat(options.get(EMFJs.OPTION_TYPE_USE)).isEqualTo(USE.URI);

		assertThat(options.containsKey(EMFJs.OPTION_USE_ID));
		assertThat(options.get(EMFJs.OPTION_USE_ID)).isEqualTo(false);

		assertThat(options.containsKey(EMFJs.OPTION_REF_FIELD)).isFalse();
		assertThat(options.containsKey(EMFJs.OPTION_ID_FIELD)).isFalse();
		assertThat(options.containsKey(EMFJs.OPTION_TYPE_FIELD)).isFalse();
		assertThat(options.containsKey(EMFJs.OPTION_TYPE_PACKAGE_URIS)).isFalse();

	}

	@Test
	public void testEMFJsonConfigNotDefault(@InjectService(timeout = 2000) ServiceAware<ExampleModelPackage> examplePackageAware,
			@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "emfJsonConfigNotDefault";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(EMFJSONConfig.class);

		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		annotationConverter.convertAnnotation(annotation, false, options);
		assertThat(options).isNotEmpty();

		assertThat(options.containsKey(EMFJs.OPTION_DATE_FORMAT));
		assertThat(options.get(EMFJs.OPTION_DATE_FORMAT)).isEqualTo("yyyy-MM-dd");

		assertThat(options.containsKey(EMFJs.OPTION_INDENT_OUTPUT));
		assertThat(options.get(EMFJs.OPTION_INDENT_OUTPUT)).isEqualTo(false);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF)).isEqualTo(true);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE)).isEqualTo(true);

		assertThat(options.containsKey(EMFJs.OPTION_SERIALIZE_TYPE));
		assertThat(options.get(EMFJs.OPTION_SERIALIZE_TYPE)).isEqualTo(false);

		assertThat(options.containsKey(EMFJs.OPTION_TYPE_USE));
		assertThat(options.get(EMFJs.OPTION_TYPE_USE)).isEqualTo(USE.NAME);

		assertThat(options.containsKey(EMFJs.OPTION_USE_ID));
		assertThat(options.get(EMFJs.OPTION_USE_ID)).isEqualTo(true);

		assertThat(options.containsKey(EMFJs.OPTION_REF_FIELD));
		assertThat(options.get(EMFJs.OPTION_REF_FIELD)).isEqualTo("ref_field");

		assertThat(options.containsKey(EMFJs.OPTION_ID_FIELD));
		assertThat(options.get(EMFJs.OPTION_ID_FIELD)).isEqualTo("id");

		assertThat(options.containsKey(EMFJs.OPTION_TYPE_FIELD));
		assertThat(options.get(EMFJs.OPTION_TYPE_FIELD)).isEqualTo("field");

		assertThat(options.containsKey(EMFJs.OPTION_TYPE_PACKAGE_URIS));
		assertThat(options.get(EMFJs.OPTION_TYPE_PACKAGE_URIS).getClass().isArray());
		String[] typePackURIs = (String[]) options.get(EMFJs.OPTION_TYPE_PACKAGE_URIS);
		assertThat(typePackURIs.length).isEqualTo(2);

	}


	@EMFJSONConfig(dateFormat = "yyyy-MM-dd", indentOutput = false, serializeContainmentAsHref = true, serializeDefaultValues = true,
			serializeTypes = false, useId = true, typeUSE = EMFJSONConfig.USE.NAME, idFieldName = "id", typeFieldName = "field", 
			refFieldName = "ref_field", typePackageUris = {"packURI1", "packURI2"})
	private void emfJsonConfigNotDefault() {

	}

	@EMFJSONConfig()
	private void emfJsonConfigDefault() {

	}

	@RootElement(rootClassUri = "http://datainmotion.com/emf/util/examplemodel/1.0#//Building")
	private void rootElementValid() {

	}

	@RootElement(rootClassUri = "")
	private void rootElementInvalid() {

	}
	
	private Method getMethod(String methodName) {
		Method method =  methodList.stream().filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
		assertThat(method).isNotNull();
		return method;
	}

	private void checkServices(ServiceAware<ExampleModelPackage> examplePackageAware, ServiceAware<AnnotationConverter> annotationConverterAware) {

		assertNotNull(examplePackageAware);
		assertThat(examplePackageAware.getServices()).hasSize(1);

		assertNotNull(annotationConverterAware);
		assertThat(annotationConverterAware.getServices()).hasSize(1);
	}
}
