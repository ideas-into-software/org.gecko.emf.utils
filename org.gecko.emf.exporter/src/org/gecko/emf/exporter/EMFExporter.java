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
package org.gecko.emf.exporter;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * Defines methods for exporting EMF resources and lists of EMF objects.
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFExporter {

	void exportResourceTo(Resource resource, OutputStream outputStream, Map<?, ?> options) throws EMFExportException;

	void exportEObjectsTo(List<EObject> eObjects, OutputStream outputStream, Map<?, ?> options)
			throws EMFExportException;
}
