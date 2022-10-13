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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emfcloud.jackson.annotations.EcoreTypeInfo.USE;
import org.eclipse.emfcloud.jackson.databind.EMFContext;
import org.gecko.emf.jaxrs.annotations.AnnotationConverter;
import org.gecko.emf.jaxrs.annotations.json.EMFJSONConfig;
import org.gecko.emf.jaxrs.annotations.json.RootElement;
import org.gecko.emf.json.constants.EMFJs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.annotation.bundle.Capability;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;

@Capability(namespace = "emf.core", name = "osgi", version = "2.1")
@Capability(namespace = "osgi.implementation", name = "osgi.jaxrs", version = "1.1")
public class EMFJsonAnnotationConverterTest  {


	List<Method> methodList;

	@BeforeEach
	public void beforeEach() {
		Method[] methods = EMFJsonAnnotationConverterTest.class.getDeclaredMethods();
		methodList = List.of(methods);		
	}

	@Test
	public void testCanHandleRootElement(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		Annotation annotation = new RootElement() {

			@Override
			public Class<? extends Annotation> annotationType() {
				return null;
			}

			@Override
			public String rootClassUri() {
				return "http://datainmotion.com/emf/util/examplemodel/1.0#//Building";
			}
		};
		assertThat(annotationConverter.canHandle(annotation, false));
	}

	@Test
	public void testCanHandleEMFJsonConfig(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "emfJsonConfigDefault";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(EMFJSONConfig.class);
		assertThat(annotationConverter.canHandle(annotation, false));
	}

	@Test
	public void testCanHandleNotValidAnnotation(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		String methodName = "beforeEach";
		Method method =  getMethod(methodName);
		Annotation annotation = method.getAnnotation(BeforeEach.class);
		assertThat(annotationConverter.canHandle(annotation, false)).isFalse();
	}

	@Test
	public void testRootElementAnnotationValid(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

		AnnotationConverter annotationConverter = annotationConverterAware.getService();
		Annotation annotation = new RootElement() {

			@Override
			public Class<? extends Annotation> annotationType() {
				return null;
			}

			@Override
			public String rootClassUri() {
				return "http://datainmotion.com/emf/util/examplemodel/1.0#//Building";
			}
		};
		Map<Object, Object> options = new HashMap<>();
		assertThat(options).isEmpty();
		annotationConverter.convertAnnotation(annotation, false, options);
		assertThat(options).isNotEmpty();
		assertThat(options.containsKey(EMFContext.Attributes.ROOT_ELEMENT));
		assertThat(options.get(EMFContext.Attributes.ROOT_ELEMENT)).isInstanceOf(EClass.class);
	}

	@Test
	public void testEMFJsonConfigDefault(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

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
	public void testEMFJsonConfigNotDefault(@InjectService(timeout = 2000, filter = "(component.name=EMFJsonAnnotationConverter)") ServiceAware<AnnotationConverter> annotationConverterAware) {

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


	@EMFJSONConfig(dateFormat = "yyyy-MM-dd", indentOutput = false, serializeDefaultValues = true,
			serializeTypes = false, useId = true, typeUSE = EMFJSONConfig.USE.NAME, idFieldName = "id", typeFieldName = "field", 
			refFieldName = "ref_field", typePackageUris = {"packURI1", "packURI2"})
	private void emfJsonConfigNotDefault() {

	}

	@EMFJSONConfig()
	private void emfJsonConfigDefault() {

	}

	private Method getMethod(String methodName) {
		Method method =  methodList.stream().filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
		assertThat(method).isNotNull();
		return method;
	}

}
