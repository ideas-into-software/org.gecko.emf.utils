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
package org.gecko.emf.exporter.keys;

import java.util.Objects;

/**
 * Composite key used in lookup index for storing information about max number of references for specific one-to-many reference.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportRefsMaxValueCountCompositeKey {
	private final String matrixName;
	private final String columnHeaderName;	
	private final String refMatrixName;
	private final String refColumnHeaderName;
	
	public EMFExportRefsMaxValueCountCompositeKey(String matrixName, String columnHeaderName, String refMatrixName,
			String refColumnHeaderName) {
		this.matrixName = matrixName;
		this.columnHeaderName = columnHeaderName;
		this.refMatrixName = refMatrixName;
		this.refColumnHeaderName = refColumnHeaderName;
	}

	public String getMatrixName() {
		return matrixName;
	}

	public String getColumnHeaderName() {
		return columnHeaderName;
	}

	public String getRefMatrixName() {
		return refMatrixName;
	}

	public String getRefColumnHeaderName() {
		return refColumnHeaderName;
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(columnHeaderName, matrixName, refColumnHeaderName, refMatrixName);
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
		EMFExportRefsMaxValueCountCompositeKey other = (EMFExportRefsMaxValueCountCompositeKey) obj;
		return Objects.equals(columnHeaderName, other.columnHeaderName) && Objects.equals(matrixName, other.matrixName)
				&& Objects.equals(refColumnHeaderName, other.refColumnHeaderName)
				&& Objects.equals(refMatrixName, other.refMatrixName);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "[matrixName=" + matrixName + ", columnHeaderName="
				+ columnHeaderName + ", refMatrixName=" + refMatrixName + ", refColumnHeaderName=" + refColumnHeaderName
				+ "]";
	}
}
