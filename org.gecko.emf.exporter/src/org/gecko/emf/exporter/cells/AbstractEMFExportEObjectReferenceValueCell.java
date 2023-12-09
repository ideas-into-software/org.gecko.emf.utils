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
 * Shared base for one / many reference wrappers.
 * 
 * See
 * {@link org.gecko.emf.exporter.cells.EMFExportEObjectOneReferenceValueCell}
 * and
 * {@link org.gecko.emf.exporter.cells.EMFExportEObjectManyReferencesValueCell}
 * 
 * 
 * @author Michal H. Siemaszko
 */
public class AbstractEMFExportEObjectReferenceValueCell implements EMFExportEObjectReferenceValueCell {
	protected final String refMatrixName;
	protected final boolean isSelfReferencingModel;

	public AbstractEMFExportEObjectReferenceValueCell(String refMatrixName, boolean isSelfReferencingModel) {
		this.refMatrixName = refMatrixName;
		this.isSelfReferencingModel = isSelfReferencingModel;
	}

	/*
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.EMFExportEObjectReferenceValueCell#getRefMatrixName()
	 */
	@Override
	public String getRefMatrixName() {
		return refMatrixName;
	}

	/*
	 * (non-Javadoc)
	 * @see org.gecko.emf.exporter.cells.EMFExportEObjectReferenceValueCell#isSelfReferencingModel()
	 */
	@Override
	public boolean isSelfReferencingModel() {
		return isSelfReferencingModel;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(isSelfReferencingModel, refMatrixName);
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
		AbstractEMFExportEObjectReferenceValueCell other = (AbstractEMFExportEObjectReferenceValueCell) obj;
		return isSelfReferencingModel == other.isSelfReferencingModel
				&& Objects.equals(refMatrixName, other.refMatrixName);
	}
}
