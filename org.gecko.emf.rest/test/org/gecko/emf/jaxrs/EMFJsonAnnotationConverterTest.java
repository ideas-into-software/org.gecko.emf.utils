package org.gecko.emf.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emfcloud.jackson.annotations.EcoreTypeInfo;
import org.eclipse.emfcloud.jackson.annotations.EcoreTypeInfo.USE;
import org.gecko.emf.jaxrs.annotations.json.EMFJSONConfig;
import org.gecko.emf.jaxrs.annotations.json.RootElement;
import org.gecko.emf.json.constants.EMFJs;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.service.component.ComponentServiceObjects;

@ExtendWith(MockitoExtension.class)
class EMFJsonAnnotationConverterTest {

	@Mock
	Annotation annotation;
	@Mock
	ComponentServiceObjects<ResourceSet> setServiceObjects;

	@InjectMocks
	EMFJsonAnnotationConverter converter = new EMFJsonAnnotationConverter();

	@Test
	void testCanHandle() {
		assertFalse(converter.canHandle(null, false));
		assertFalse(converter.canHandle(annotation, false));
	}
	
	@Test
	void testSwitchUse() {
		assertEquals(USE.CLASS,EMFJsonAnnotationConverter.switchUse(EMFJSONConfig.USE.CLASS));
		assertEquals(USE.NAME,EMFJsonAnnotationConverter.switchUse(EMFJSONConfig.USE.NAME));
		assertEquals(USE.URI,EMFJsonAnnotationConverter.switchUse(EMFJSONConfig.USE.URI));
	}

	@Nested
	class AnnotationEMFJSONConfig {
		@Mock
		EMFJSONConfig emfJSONConfigAnnotation;

		@EMFJSONConfig
		AnnotationEMFJSONConfig() {
		}

		@Test
		void testCanHandle() {
			assertTrue(converter.canHandle(emfJSONConfigAnnotation, false));
		}

		@Test
		void testConvertAnnotationDefault() throws Exception {
			Map<Object, Object> options = new HashMap<>();

			EMFJSONConfig defaultEMFJSONConfigAnnotation = this.getClass().getDeclaredConstructors()[0]
					.getAnnotation(EMFJSONConfig.class);

			converter.convertAnnotation(defaultEMFJSONConfigAnnotation, false, options);

			assertFalse(options.containsValue(EMFJs.OPTION_DATE_FORMAT));
			assertFalse(options.containsValue(EMFJs.OPTION_REF_FIELD));
			assertFalse(options.containsValue(EMFJs.OPTION_ID_FIELD));
			assertFalse(options.containsValue(EMFJs.OPTION_TYPE_FIELD));
			assertFalse(options.containsValue(EMFJs.OPTION_TYPE_PACKAGE_URI));
			assertTrue((Boolean) options.get(EMFJs.OPTION_INDENT_OUTPUT));
			assertFalse((Boolean) options.get(EMFJs.OPTION_USE_ID));
			assertFalse((Boolean) options.get(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF));
			assertFalse((Boolean) options.get(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE));
			assertTrue((Boolean) options.get(EMFJs.OPTION_SERIALIZE_TYPE));
			assertEquals(EcoreTypeInfo.USE.URI, options.get(EMFJs.OPTION_TYPE_USE));
		}

		@Test
		void testConvertAnnotation() throws Exception {
			Map<Object, Object> options = new HashMap<>();
			when(emfJSONConfigAnnotation.dateFormat()).thenReturn("myDateFormat");
			when(emfJSONConfigAnnotation.refFieldName()).thenReturn("myRefFieldName");
			when(emfJSONConfigAnnotation.idFieldName()).thenReturn("myIdFieldName");
			when(emfJSONConfigAnnotation.indentOutput()).thenReturn(true);
			when(emfJSONConfigAnnotation.useId()).thenReturn(true);
			when(emfJSONConfigAnnotation.serializeContainmentAsHref()).thenReturn(true);
			when(emfJSONConfigAnnotation.serializeDefaultValues()).thenReturn(true);
			when(emfJSONConfigAnnotation.serializeTypes()).thenReturn(true);
			when(emfJSONConfigAnnotation.typeFieldName()).thenReturn("myTypeFieldName");
			when(emfJSONConfigAnnotation.typePackageUri()).thenReturn("myTypePackageUri");
			when(emfJSONConfigAnnotation.typeUSE()).thenReturn(EMFJSONConfig.USE.NAME);

			converter.convertAnnotation(emfJSONConfigAnnotation, false, options);

			assertEquals("myDateFormat", options.get(EMFJs.OPTION_DATE_FORMAT));
			assertEquals("myRefFieldName", options.get(EMFJs.OPTION_REF_FIELD));
			assertEquals("myIdFieldName", options.get(EMFJs.OPTION_ID_FIELD));
			assertEquals("myTypeFieldName", options.get(EMFJs.OPTION_TYPE_FIELD));
			assertEquals("myTypePackageUri", options.get(EMFJs.OPTION_TYPE_PACKAGE_URI));
			assertTrue((Boolean) options.get(EMFJs.OPTION_INDENT_OUTPUT));
			assertTrue((Boolean) options.get(EMFJs.OPTION_USE_ID));
			assertTrue((Boolean) options.get(EMFJs.OPTION_SERIALIZE_CONTAINMENT_AS_HREF));
			assertTrue((Boolean) options.get(EMFJs.OPTION_SERIALIZE_DEFAULT_VALUE));
			assertTrue((Boolean) options.get(EMFJs.OPTION_SERIALIZE_TYPE));
			assertEquals(EcoreTypeInfo.USE.NAME, options.get(EMFJs.OPTION_TYPE_USE));
		}
	}

	@Nested
	class AnnotationRootElement {

		@Mock
		RootElement rootElementAnnotation;
		@Mock
		EClass eClass;
		@Mock
		ResourceSet set;

		@Captor
		ArgumentCaptor<URI> uri;

		@Test
		void testCanHandle() {
			assertTrue(converter.canHandle(rootElementAnnotation, false));
		}

		@Test
		void testConvertAnnotationRoot() {
			Map<Object, Object> map = new HashMap<>();
			when(setServiceObjects.getService()).thenReturn(set);
			when(set.getEObject(any(), anyBoolean())).thenReturn(eClass);
			when(rootElementAnnotation.rootClassUri()).thenReturn("MyClassUri");

			converter.convertAnnotation(rootElementAnnotation, false, map);

			verify(set).getEObject(uri.capture(), eq(true));
			assertEquals("MyClassUri", uri.getValue().path());
		}
	}
}
