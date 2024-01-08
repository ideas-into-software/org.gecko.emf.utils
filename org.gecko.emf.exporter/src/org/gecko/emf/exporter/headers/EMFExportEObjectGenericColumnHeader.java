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
package org.gecko.emf.exporter.headers;

/**
 * Header for columns which hold EObject's all other values (i.e. not ID
 * {@link org.gecko.emf.exporter.headers.EMFExportEObjectIDColumnHeader}, one
 * reference
 * {@link org.gecko.emf.exporter.headers.EMFExportEObjectOneReferenceColumnHeader}
 * or many reference values
 * {@link org.gecko.emf.exporter.headers.EMFExportEObjectManyReferencesColumnHeader}).
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportEObjectGenericColumnHeader extends AbstractEMFExportEObjectColumnHeader
		implements EMFExportEObjectColumnHeader {

	public EMFExportEObjectGenericColumnHeader(String matrixName, String columnHeaderName) {
		super(matrixName, columnHeaderName);
	}
}
