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

import java.util.HashMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.emf.common.util.URI;
import org.gecko.emf.json.configuration.ConfigurableJsonResource;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;

/**
 * 
 * @author ilenia
 * @since Aug 2, 2022
 */
public class ConfigurableJsonResourceConstructorTest {
	
	@Test
	public void testOneParConstructor() {
		
		String pathToJsonInputFile = System.getProperty("base.path") + "/data/exampleTypeByName.json";
		URI uri = URI.createFileURI(pathToJsonInputFile);
		ConfigurableJsonResource res = new ConfigurableJsonResource(uri);
		ObjectMapper mapper = res.configureMapper(new HashMap<Object, Object>());
		assertThat(mapper).isNotNull();
		assertThat(mapper.getDateFormat()).isNotNull();
	}

	@Test
	public void testTwoParConstructor() {
		
		String pathToJsonInputFile = System.getProperty("base.path") + "/data/exampleTypeByName.json";
		URI uri = URI.createFileURI(pathToJsonInputFile);
		ObjectMapper objMapper = new ObjectMapper();
		ConfigurableJsonResource res = new ConfigurableJsonResource(uri, objMapper);
		ObjectMapper mapper = res.configureMapper(new HashMap<Object, Object>());
		assertThat(mapper).isNotNull();
		assertThat(mapper.getDateFormat()).isInstanceOf(StdDateFormat.class);
	}
}
