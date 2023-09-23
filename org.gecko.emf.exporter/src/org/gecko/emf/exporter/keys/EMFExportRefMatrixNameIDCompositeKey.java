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
 * Composite key used in lookup index for storing information about position (row number) of specific reference.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportRefMatrixNameIDCompositeKey {
	private final String refMatrixName;
	private final String refID;
	
	public EMFExportRefMatrixNameIDCompositeKey(String refMatrixName, String refID) {
		this.refMatrixName = refMatrixName;
		this.refID = refID;
	}

	public String getRefMatrixName() {
		return refMatrixName;
	}

	public String getRefID() {
		return refID;
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(refID, refMatrixName);
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
		EMFExportRefMatrixNameIDCompositeKey other = (EMFExportRefMatrixNameIDCompositeKey) obj;
		return Objects.equals(refID, other.refID) && Objects.equals(refMatrixName, other.refMatrixName);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "[refMatrixName=" + refMatrixName + ", refID=" + refID + "]";
	}
}
