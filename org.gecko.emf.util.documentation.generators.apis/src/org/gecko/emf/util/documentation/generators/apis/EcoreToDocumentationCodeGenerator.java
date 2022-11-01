/**
 * Copyright (c) 2012 - 2022 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.util.documentation.generators.apis;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;

/**
 * 
 * @author ilenia
 * @since Oct 19, 2022
 */
public interface EcoreToDocumentationCodeGenerator {
	
	CharSequence generateDocumentation(EPackage ePackage, EcoreToDocumentationOptions docGenOption);
		
	CharSequence generateDocumentation(EClass eClass, EcoreToDocumentationOptions docGenOption);

}
