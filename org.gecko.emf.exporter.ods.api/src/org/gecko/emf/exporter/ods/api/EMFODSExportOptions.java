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
package org.gecko.emf.exporter.ods.api;

import org.gecko.emf.exporter.EMFExportOptions;

/**
 * ODS export options.
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFODSExportOptions extends EMFExportOptions {

	// automatically adjust column width based on contents
	String OPTION_ADJUST_COLUMN_WIDTH = "ADJUST_COLUMN_WIDTH"; // TODO: extract to org.gecko.emf.exporter.ods.EMFODSExportOptions

	// generate links for references
	String OPTION_GENERATE_LINKS = "GENERATE_LINKS"; // TODO: extract to org.gecko.emf.exporter.ods.EMFODSExportOptions

	// TODO: freezing rows is currently not supported in SODS
	// freeze header row
//	String OPTION_FREEZE_HEADER_ROW = "FREEZE_HEADER_ROW";
}
