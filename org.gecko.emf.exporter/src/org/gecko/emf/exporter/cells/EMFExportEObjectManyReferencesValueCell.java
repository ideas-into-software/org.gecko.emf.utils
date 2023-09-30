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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper for cells which hold EObject's many reference values.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportEObjectManyReferencesValueCell extends AbstractEMFExportEObjectReferenceValueCell implements EMFExportEObjectReferenceValueCell {
	private final List<String> refIDs;
	private final List<String> refURIs;

	public EMFExportEObjectManyReferencesValueCell(String refMatrixName, List<String> refIDs, List<String> refURIs) {
		super(refMatrixName);

		this.refIDs = refIDs;
		this.refURIs = refURIs;
	}

	public List<String> getRefIDs() {
		return refIDs;
	}

	public boolean hasRefIDs() {
		return (refIDs != null) && !refIDs.isEmpty();
	}

	public int getRefIDsCount() {
		if (hasRefIDs()) {
			return refIDs.size();
		} else {
			return 0;
		}
	}

	public List<String> getURIs() {
		return refURIs;
	}

	public boolean hasURIs() {
		return (refURIs != null) && !refURIs.isEmpty();
	}

	public int getURIsCount() {
		if (hasURIs()) {
			return refURIs.size();
		} else {
			return 0;
		}
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(refMatrixName, refURIs, refIDs);
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
		EMFExportEObjectManyReferencesValueCell other = (EMFExportEObjectManyReferencesValueCell) obj;
		return Objects.equals(refMatrixName, other.refMatrixName) && Objects.equals(refURIs, other.refURIs)
				&& Objects.equals(refIDs, other.refIDs);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		if (hasRefIDs()) {
			return Arrays.toString(refIDs.toArray()); 
		} else {
			return null;
		}
	}
}
