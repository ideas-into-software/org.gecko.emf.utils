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
	private final String refID;
	private final String refURI;

	public EMFExportEObjectOneReferenceValueCell(String refMatrixName, String refID, String refURI) {
		super(refMatrixName);

		this.refID = refID;
		this.refURI = refURI;
	}

	public String getRefID() {
		return refID;
	}

	public boolean hasRefID() {
		return (refID != null);
	}

	public String getURI() {
		return refURI;
	}

	public boolean hasURI() {
		return (refURI != null);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(refMatrixName, refURI, refID);
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
		return Objects.equals(refMatrixName, other.refMatrixName) && Objects.equals(refURI, other.refURI)
				&& Objects.equals(refID, other.refID);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return refID;
	}
}
