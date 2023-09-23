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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createBasicPackageResourceSet;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createBusinessPerson;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createFlintstonesFamily;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createRequest;
import static org.gecko.emf.csv.tests.helper.EMFCSVExporterTestHelper.createSimpsonFamily;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.gecko.emf.exporter.EMFExportException;
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

import trees.TreesPackage;

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
	
	@Test
	public void testExportExampleModelBasicInvalidClassHierarchyException(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile("testExportExampleModelBasicInvalidClassHierarchyException", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// having 'BusinessPerson' instance passed in list of objects to be exported
		// will fail class hierarchy validation, triggered when FLAT export mode is
		// chosen - instance of 'Family' is passed as first element and it determines
		// valid class hierarchy
		assertThatExceptionOfType(EMFExportException.class).isThrownBy(() -> {
			// @formatter:off
			emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
					Map.of(
							EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//							EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//							EMFExportOptions.OPTION_EXPORT_METADATA, false, // defaults to false in FLAT export mode
//							EMFExportOptions.OPTION_ADD_MAPPING_TABLE, false, // defaults to false in FLAT export mode
							EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
						)
					);
			// @formatter:on
		});
	}
	
	@Test
	public void testExportExampleModelBasicExportNonContainmentDisabledAddMappingTableEnabledException(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile(
				"testExportExampleModelBasicExportNonContainmentDisabledAddMappingTableEnabledException", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// incompatible combination of export options: 'export non-containment
		// references' option cannot be turned off if 'generate mapping table' option is
		// turned on!
		assertThatExceptionOfType(EMFExportException.class).isThrownBy(() -> {
			// @formatter:off
			emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
					Map.of(
							EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
							EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, false, // defaults to true
//							EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to true
							EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
						)
					);
			// @formatter:on
		});
	}
	
	@Test
	public void testExportExampleModelBasicFlatExportModeNoExportMetadataNorAddMappingTableEnabledException(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile(
				"testExportExampleModelBasicFlatExportModeNoExportMetadataNorAddMappingTableEnabledException", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// incompatible combination of export options: neither 'export metadata' nor
		// 'generate mapping table' options can be turned on in flat CSV export mode!
		assertThatExceptionOfType(EMFExportException.class).isThrownBy(() -> {
			// @formatter:off
			emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
					Map.of(
							EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//							EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
							EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to false in FLAT export mode
							EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
						)
					);
			// @formatter:on
		});
	}	

	@Test
	public void testExportExampleModelBasicEObjectsToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		Path filePath = Files.createTempFile("testExportExampleModelBasicEObjectsToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);
		
		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, false, // defaults to false in FLAT export mode
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, false, // defaults to false in FLAT export mode
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}

	@Test
	public void testExportExampleModelBasicEObjectsToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Family simpsonFamily = createSimpsonFamily(basicFactory);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);

		Path filePath = Files.createTempFile("testExportExampleModelBasicEObjectsToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(simpsonFamily, flintstonesFamily, businessPerson), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, true, // defaults to true
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to true
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}
	
	@Test
	public void testExportExampleModelBasicResourceToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportExampleModelBasicResourceToCsvFlatMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		Path filePath = Files.createTempFile("testExportExampleModelBasicResourceToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, false, // defaults to false in FLAT export mode
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, false, // defaults to false in FLAT export mode
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}
	
	@Test
	public void testExportExampleModelBasicResourceToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware,
			@InjectService BasicFactory basicFactory, @InjectService BasicPackage basicPackage) throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		ResourceSet resourceSet = createBasicPackageResourceSet(basicPackage);
		Resource xmiResource = resourceSet
				.createResource(URI.createURI("testExportExampleModelBasicResourceToCsvZipMode.test"));
		assertNotNull(xmiResource);

		Family simpsonFamily = createSimpsonFamily(basicFactory);
		xmiResource.getContents().add(simpsonFamily);

		Family flintstonesFamily = createFlintstonesFamily(basicFactory);
		xmiResource.getContents().add(flintstonesFamily);

		BusinessPerson businessPerson = createBusinessPerson(basicFactory);
		xmiResource.getContents().add(businessPerson);

		Path filePath = Files.createTempFile("testExportExampleModelBasicResourceToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportResourceTo(xmiResource, fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, true, // defaults to true
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to true
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}
	
	@Test
	public void testExportUtilModelEObjectsToCsvFlatMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Request request1 = createRequest(UtilitiesFactory.eINSTANCE);
		
		Request request2 = createRequest(UtilitiesFactory.eINSTANCE);
		
		Request request3 = createRequest(UtilitiesFactory.eINSTANCE);		

		Path filePath = Files.createTempFile("testExportUtilModelEObjectsToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(request1, request2, request3), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, false, // defaults to false in FLAT export mode
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, false, // defaults to false in FLAT export mode
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}
	
	@Test
	public void testExportUtilModelEObjectsToCsvZipMode(
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		Request request1 = createRequest(UtilitiesFactory.eINSTANCE);
		
		Request request2 = createRequest(UtilitiesFactory.eINSTANCE);
		
		Request request3 = createRequest(UtilitiesFactory.eINSTANCE);		

		Path filePath = Files.createTempFile("testExportUtilModelEObjectsToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(List.of(request1, request2, request3), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, true, // defaults to true
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to true
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}

	private static final String TREES_DATASET_XMI = System.getProperty("TREES_DATASET_XMI");	

	@Test
	public void testExportTreesModelEObjectsToCsvFlatMode(
			@InjectService(timeout = 2000) ServiceAware<ResourceSet> rsAware,
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertNotNull(rsAware);
		assertThat(rsAware.getServices()).hasSize(1);
		ResourceSet resourceSet = rsAware.getService();
		assertNotNull(resourceSet);		
		
		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		// register model
		EPackage.Registry packageRegistry = resourceSet.getPackageRegistry();
		packageRegistry.put(TreesPackage.eNS_URI, TreesPackage.eINSTANCE);
        
		// register xmi
		Map<String, Object> extensionFactoryMap = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
        extensionFactoryMap.put("xmi", new XMIResourceFactoryImpl());
        
        Resource resource = resourceSet.getResource(URI.createFileURI(new File(TREES_DATASET_XMI).getAbsolutePath()), true);		
		
		Path filePath = Files.createTempFile("testExportTreesModelEObjectsToCsvFlatMode", ".csv");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(resource.getContents(), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, false, // defaults to false in FLAT export mode
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, false, // defaults to false in FLAT export mode
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.FLAT
					)
				);
		// @formatter:on
	}
	
	@Test
	public void testExportTreesModelEObjectsToCsvZipMode(
			@InjectService(timeout = 2000) ServiceAware<ResourceSet> rsAware,
			@InjectService(cardinality = 1, timeout = 4000, filter = "(component.name=EMFCSVExporter)") ServiceAware<EMFExporter> emfCsvExporterAware)
			throws Exception {

		assertNotNull(rsAware);
		assertThat(rsAware.getServices()).hasSize(1);
		ResourceSet resourceSet = rsAware.getService();
		assertNotNull(resourceSet);		
		
		assertThat(emfCsvExporterAware.getServices()).hasSize(1);
		EMFExporter emfCsvExporterService = emfCsvExporterAware.getService();
		assertThat(emfCsvExporterService).isNotNull();

		// register model
		EPackage.Registry packageRegistry = resourceSet.getPackageRegistry();
		packageRegistry.put(TreesPackage.eNS_URI, TreesPackage.eINSTANCE);
        
		// register xmi
		Map<String, Object> extensionFactoryMap = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
        extensionFactoryMap.put("xmi", new XMIResourceFactoryImpl());
        
        Resource resource = resourceSet.getResource(URI.createFileURI(new File(TREES_DATASET_XMI).getAbsolutePath()), true);		
		
		Path filePath = Files.createTempFile("testExportTreesModelEObjectsToCsvZipMode", ".zip");

		OutputStream fileOutputStream = Files.newOutputStream(filePath);

		// @formatter:off
		emfCsvExporterService.exportEObjectsTo(resource.getContents(), fileOutputStream, 
				Map.of(
						EMFExportOptions.OPTION_LOCALE, Locale.GERMANY,
//						EMFExportOptions.OPTION_EXPORT_NONCONTAINMENT, true, // defaults to true
//						EMFExportOptions.OPTION_EXPORT_METADATA, true, // defaults to true
//						EMFExportOptions.OPTION_ADD_MAPPING_TABLE, true, // defaults to true
						EMFCSVExportOptions.OPTION_EXPORT_MODE, EMFCSVExportMode.ZIP
					)
				);
		// @formatter:on
	}
}
