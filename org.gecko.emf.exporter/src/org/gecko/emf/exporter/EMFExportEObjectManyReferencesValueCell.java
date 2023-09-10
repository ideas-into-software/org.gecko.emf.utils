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
package org.gecko.emf.exporter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper for cells which hold EObject's many reference values.
 * 
 * @author Michal H. Siemaszko
 */
public class EMFExportEObjectManyReferencesValueCell {
	private final List<String> values;

	public EMFExportEObjectManyReferencesValueCell(List<String> values) {
		this.values = values;
	}
	
	public List<String> getValues() {
		return values;
	}
	
	public int getValuesCount() {
		return values.size();
	}	

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(values);
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
		EMFExportEObjectManyReferencesValueCell other = (EMFExportEObjectManyReferencesValueCell) obj;
		return Objects.equals(values, other.values);
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return Arrays.toString(values.toArray());
	}
}
