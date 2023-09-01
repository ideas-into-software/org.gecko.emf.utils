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
package org.gecko.emf.exporter.csv.api;

import org.gecko.emf.exporter.EMFExportOptions;

/**
 * CSV export options.
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFCSVExportOptions extends EMFExportOptions {
	
	// export mode to use
	String OPTION_EXPORT_MODE = "OPTION_EXPORT_MODE";
}
