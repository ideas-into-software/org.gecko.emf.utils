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
package org.gecko.emf.exporter.r_lang.api;

import org.gecko.emf.exporter.EMFExportOptions;

/**
 * R Language export options.
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFRLangExportOptions extends EMFExportOptions {

	// one data frame per RData file, contained in ZIP archive; otherwise all data
	// frames will be output to same RData file (list of data frames)
	String OPTION_DATAFRAME_PER_FILE = "DATAFRAME_PER_FILE";
}
