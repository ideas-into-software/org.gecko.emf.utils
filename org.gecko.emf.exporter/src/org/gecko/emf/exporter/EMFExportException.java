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

/**
 * Exception which may be thrown during EMF export.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportException extends Exception {

	/** serialVersionUID */
	private static final long serialVersionUID = 8114199181901603984L;

	public EMFExportException(String msg) {
		super(msg);
	}

	public EMFExportException(Throwable cause) {
		super(cause);
	}
}
