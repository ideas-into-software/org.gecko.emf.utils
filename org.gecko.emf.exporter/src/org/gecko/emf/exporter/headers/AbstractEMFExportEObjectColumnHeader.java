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
 * Shared base for all column headers.
 * 
 * @author Michal H. Siemaszko
 */
public abstract class AbstractEMFExportEObjectColumnHeader implements EMFExportEObjectColumnHeader {
	protected final String matrixName;
	protected final String columnHeaderName;
	
	public AbstractEMFExportEObjectColumnHeader(String matrixName, String columnHeaderName) {
		this.matrixName = matrixName;
		this.columnHeaderName = columnHeaderName;
	}
	
	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExportEObjectColumnHeader#getMatrixName()
	 */
	@Override
	public String getMatrixName() {
		return matrixName;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExportEObjectColumnHeader#getColumnHeaderName()
	 */
	@Override
	public String getColumnHeaderName() {
		return columnHeaderName;
	}	

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(columnHeaderName, matrixName);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractEMFExportEObjectColumnHeader other = (AbstractEMFExportEObjectColumnHeader) obj;
		return Objects.equals(columnHeaderName, other.columnHeaderName) && Objects.equals(matrixName, other.matrixName);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return columnHeaderName;
	}
}
