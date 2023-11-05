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

import java.util.Objects;

/**
 * Shared base for one / many reference column headers.
 * 
 * @author Michal H. Siemaszko
 */
public class AbstractEMFExportEObjectReferenceColumnHeader extends AbstractEMFExportEObjectColumnHeader implements EMFExportEObjectReferenceColumnHeader {
	private final String refMatrixName;
	
	public AbstractEMFExportEObjectReferenceColumnHeader(String matrixName, String refMatrixName, String columnHeaderName) {
		super(matrixName, columnHeaderName);
		
		this.refMatrixName = refMatrixName;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExportEObjectReferenceColumnHeader#getRefMatrixName()
	 */
	@Override
	public String getRefMatrixName() {
		return refMatrixName;
	}	

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(columnHeaderName, matrixName, refMatrixName);
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
		AbstractEMFExportEObjectReferenceColumnHeader other = (AbstractEMFExportEObjectReferenceColumnHeader) obj;
		return Objects.equals(columnHeaderName, other.columnHeaderName) && Objects.equals(matrixName, other.matrixName)
				&& Objects.equals(refMatrixName, other.refMatrixName);
	}
}
