/**
 * Copyright (c) 2012 - 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.ods.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gecko.emf.ods.tests.helper.EMFODSExporterTestHelper.createBusinessPerson;
import static org.gecko.emf.ods.tests.helper.EMFODSExporterTestHelper.createFlintstonesFamily;
import static org.gecko.emf.ods.tests.helper.EMFODSExporterTestHelper.createResourceSet;
import static org.gecko.emf.ods.tests.helper.EMFODSExporterTestHelper.createSimpsonFamily;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.exporter.EMFExportOptions;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.osgi.example.model.basic.BasicFactory;
import org.gecko.emf.osgi.example.model.basic.BasicPackage;
import org.gecko.emf.osgi.example.model.basic.BusinessPerson;
import org.gecko.emf.osgi.example.model.basic.Family;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.annotation.Testable;
import org.osgi.framework.ServiceReference;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

@Testable
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EMFODSExporterTest {
	
	@Order(value=-1)
	@Test
	public void testServices(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFODSExporter)") ServiceAware<EMFExporter> emfOdsExporterAware) {

		assertThat(emfOdsExporterAware.getServices()).hasSize(1);
		ServiceReference<EMFExporter> emfOdsExporterReference = emfOdsExporterAware.getServiceReference();
		assertThat(emfOdsExporterReference).isNotNull();
	}
	
	@Test
	public void testExportResourceToOds(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFODSExporter)") ServiceAware<EMFExporter> emfOdsExporterAware,
			@InjectService BasicFactory bf, @InjectService BasicPackage bp) throws Exception {

		assertThat(emfOdsExporterAware.getServices()).hasSize(1);
		EMFExporter emfOdsExporterService = emfOdsExporterAware.getService();
		assertThat(emfOdsExporterService).isNotNull();

		ResourceSet resourceSet = createResourceSet(bp);
		Resource xmiResource = resourceSet.createResource(URI.createURI("exporter.test"));
		assertNotNull(xmiResource);
		
		Family simpsonFamily = createSimpsonFamily(bf);
		xmiResource.getContents().add(simpsonFamily);
		
		Family flintstonesFamily = createFlintstonesFamily(bf);
		xmiResource.getContents().add(flintstonesFamily);
		
		BusinessPerson businessPerson = createBusinessPerson(bf);
		xmiResource.getContents().add(businessPerson);
		
		Path filePath = Files.createTempFile("testExport", ".ods");
		
		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		emfOdsExporterService.exportResourceTo(xmiResource, Locale.GERMANY, fileOutputStream, 
				Map.of(EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true,
						EMFExportOptions.OPTION_EXPORT_METADATA, true));
	}
}
