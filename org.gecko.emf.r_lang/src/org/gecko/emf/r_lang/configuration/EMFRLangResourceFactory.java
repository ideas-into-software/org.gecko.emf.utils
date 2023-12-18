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
package org.gecko.emf.r_lang.configuration;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl;
import org.gecko.emf.exporter.EMFExporter;
import org.gecko.emf.exporter.EMFExporterConstants;
import org.gecko.emf.exporter.r_lang.api.EMFRLangExporterConstants;
import org.osgi.annotation.bundle.Capability;
import org.osgi.annotation.bundle.Requirement;
import org.gecko.emf.r_lang.constants.EMFRLangConstants;

/**
 * {@link ResourceFactoryImpl} for the R Language resource.
 * 
 * @author Michal H. Siemaszko
 */
@Requirement(namespace = EMFExporterConstants.EMF_EXPORTER_NAMESPACE, name = EMFRLangExporterConstants.EMF_EXPORTER_NAME)
@Capability(namespace = EMFRLangConstants.EMF_RESOURCEFACTORY_NAMESPACE, name = EMFRLangConstants.EMF_RESOURCEFACTORY_NAME)
public class EMFRLangResourceFactory extends ResourceFactoryImpl {
	private EMFExporter emfRLangExporter;

	/**
	 * Creates a new instance.
	 */
	public EMFRLangResourceFactory() {
		super();
	}

	public EMFRLangResourceFactory(EMFExporter emfRLangExporter) {
		super();

		this.emfRLangExporter = emfRLangExporter;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.eclipse.emf.ecore.resource.impl.ResourceFactoryImpl#createResource(org.eclipse.emf.common.util.URI)
	 */
	@Override
	public Resource createResource(URI uri) {
		if (emfRLangExporter != null) {
			return new EMFRLangResource(uri, emfRLangExporter);
		} else {
			return new EMFRLangResource(uri);
		}
	}
}
