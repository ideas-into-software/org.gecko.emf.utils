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
package org.gecko.emf.xlsx.configuration;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.EMFExporterConstants;
import org.gecko.emf.exporter.xlsx.api.EMFXLSXExporterConstants;
import org.gecko.emf.xlsx.constants.EMFXLSXConstants;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;

/**
 * {@link ResourceFactoryImpl} for the XLSX resource.
 * 
 * @author Michal H. Siemaszko
 */
@Requirement(namespace = EMFExporterConstants.EMF_EXPORTER_NAMESPACE, name = EMFXLSXExporterConstants.EMF_EXPORTER_NAME)
@Capability(namespace = EMFXLSXConstants.EMF_RESOURCEFACTORY_NAMESPACE, name = EMFXLSXConstants.EMF_RESOURCEFACTORY_NAME)
public class EMFXLSXResourceFactory extends ResourceFactoryImpl {
	private EMFExporter emfXLSXExporter;

	/**
	 * Creates a new instance.
	 */
	public EMFXLSXResourceFactory() {
		super();
	}

	public EMFXLSXResourceFactory(EMFExporter emfXLSXExporter) {
		super();

		this.emfXLSXExporter = emfXLSXExporter;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl#createResource(org.eclipse.emf.common.util.URI)
	 */	
	@Override
	public Resource createResource(URI uri) {
		if (emfXLSXExporter != null) {
			return new EMFXLSXResource(uri, emfXLSXExporter);
		} else {
			return new EMFXLSXResource(uri);
		}
	}
}
