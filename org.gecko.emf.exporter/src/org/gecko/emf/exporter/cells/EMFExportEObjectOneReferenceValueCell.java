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

import java.util.Objects;

/**
 * Wrapper for cells which hold EObject's one reference value.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportEObjectOneReferenceValueCell extends AbstractEMFExportEObjectReferenceValueCell implements EMFExportEObjectReferenceValueCell {
	private final String value;

	public EMFExportEObjectOneReferenceValueCell(String refMatrixName, String value) {
		super(refMatrixName);

		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public boolean hasValue() {
		return (value != null);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(refMatrixName, value);
		return result;
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		EMFExportEObjectOneReferenceValueCell other = (EMFExportEObjectOneReferenceValueCell) obj;
		return Objects.equals(refMatrixName, other.refMatrixName) && Objects.equals(value, other.value);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return value;
	}
}
