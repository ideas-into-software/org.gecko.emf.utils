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
package org.gecko.emf.ods.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.gecko.emf.exporter.EMFExportException;
import org.gecko.emf.exporter.EMFExporter;

/**
 * A Resource implementation that writes its content in ODS.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFODSResource extends ResourceImpl {
	private EMFExporter emfODSExporter;

	public EMFODSResource(URI uri) {
		super(uri);
	}

	public EMFODSResource(URI uri, EMFExporter emfODSExporter) {
		super(uri);

		this.emfODSExporter = emfODSExporter;
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
		if (options == null) {
			options = Collections.<String, Object>emptyMap();
		}

		if (emfODSExporter != null) {
			try {
				emfODSExporter.exportResourceTo(this, outputStream, options);
			} catch (EMFExportException e) {
				throw new IOException(e);
			}
		}
	}
}
