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
package org.gecko.emf.exporter.cells;

/**
 * Shared API for one / many reference wrappers.
 * 
 * See
 * {@link org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell}
 * and
 * {@link org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell}
 * 
 * 
 * @author Michal H. Siemaszko
 */
public interface EMFExportEObjectReferenceValueCell {

	String getRefMatrixName();
}
