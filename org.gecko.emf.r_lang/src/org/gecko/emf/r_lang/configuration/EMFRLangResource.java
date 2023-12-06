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
 * A Resource implementation that writes its content as R Language data-frame.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFRLangResource extends ResourceImpl {
	private EMFExporter emfRLangExporter;

	public EMFRLangResource(URI uri) {
		super(uri);
	}

	public EMFRLangResource(URI uri, EMFExporter emfRLangExporter) {
		super(uri);

		this.emfRLangExporter = emfRLangExporter;
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

		if (emfRLangExporter != null) {
			try {
				emfRLangExporter.exportResourceTo(this, outputStream, options);
			} catch (EMFExportException e) {
				throw new IOException(e);
			}
		}
	}
}
