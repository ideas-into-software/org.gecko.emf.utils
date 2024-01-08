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
package org.gecko.emf.exporter.xlsx.api;

import org.gecko.emf.exporter.EMFExportOptions;

/**
 * XLSX export options.
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFXLSXExportOptions extends EMFExportOptions {

	// automatically adjust column width based on contents
	String OPTION_ADJUST_COLUMN_WIDTH = "ADJUST_COLUMN_WIDTH";

	// generate links for references
	String OPTION_GENERATE_LINKS = "GENERATE_LINKS";

	// freeze header row
	String OPTION_FREEZE_HEADER_ROW = "FREEZE_HEADER_ROW";
}
