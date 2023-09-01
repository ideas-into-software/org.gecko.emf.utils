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
package org.gecko.emf.csv.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createBasicPackageResourceSet;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createBusinessPerson;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createFlintstonesFamily;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createRequest;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createSimpsonFamily;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.exporter.EMFExportOptions;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.csv.api.EMFCSVExportMode;
import org.gecko.emf.exporter.csv.api.EMFCSVExportOptions;
import org.gecko.emf.osgi.example.model.basic.BasicFactory;
import org.gecko.emf.osgi.example.model.basic.BasicPackage;
import org.gecko.emf.osgi.example.model.basic.BusinessPerson;
import org.gecko.emf.osgi.example.model.basic.Family;
import org.gecko.emf.utilities.Request;
import org.gecko.emf.utilities.UtilitiesFactory;
import org.junit.jupiter.api.Disabled;
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

/**
 * EMF CSV exporter integration test.
 * 
 * @author Michal H. Siemaszko
 */
@Testable
@ExtendWith(BundleContextExtension.class)
@ExtendWith(ServiceExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EMFCSVExporterTest {

	@Order(value = -1)
	@Test
	public void testServices(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware) {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		ServiceReference<EMFExporter> emfCsvExporterReference = emfCsvExporterAware.getServiceReference();
		assertThat(emfCsvExporterReference).isNotNull();
	}

	@Disabled
	@Test
	public void testExportBasicPackageResourceToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportBasicPackageResourceToCsvFlatMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);
		xmiResource.getContents().add(businessPerson);

		Path filePath = Files.createTempFile("testExportBasicPackageResourceToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}

	@Test
	public void testExportBasicPackageResourceToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportBasicPackageResourceToCsvZipMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);
		xmiResource.getContents().add(businessPerson);

		Path filePath = Files.createTempFile("testExportBasicPackageResourceToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}

	@Disabled
	@Test
	public void testExportBasicPackageEObjectsToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile("testExportBasicPackageEObjectsToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}

	@Test
	public void testExportBasicPackageEObjectsToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile("testExportBasicPackageEObjectsToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}

	@Disabled
	@Test
	public void testExportUtilitiesPackageEObjectsToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Request request = createRequest(UtilitiesFactory.eINSTANCE);

		Path filePath = Files.createTempFile("testExportUtilitiesPackageEObjectsToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(request), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}

	@Test
	public void testExportUtilitiesPackageEObjectsToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Request request = createRequest(UtilitiesFactory.eINSTANCE);

		Path filePath = Files.createTempFile("testExportUtilitiesPackageEObjectsToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(request), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}

	@Disabled
	@Test
	public void testExportUtilitiesPackageResourceToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportUtilitiesPackageResourceToCsvFlatMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);
		xmiResource.getContents().add(businessPerson);

		Path filePath = Files.createTempFile("testExportUtilitiesPackageResourceToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}

	@Test
	public void testExportUtilitiesPackageResourceToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportUtilitiesPackageResourceToCsvZipMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);
		xmiResource.getContents().add(businessPerson);

		Path filePath = Files.createTempFile("testExportUtilitiesPackageResourceToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, 
						EMFExportOptions.OPTION_EXPORT_METADATA, true,
						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true,
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}
}
