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
package org.gecko.emf.r_lang.annotation;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.gecko.emf.osgi.EMFNamespaces;
import org.gecko.emf.osgi.ResourceSetConfigurator;
import org.gecko.emf.osgi.annotation.require.RequireEMF;
import org.gecko.emf.r_lang.constants.EMFRLangConstants;
import org.osgi.annotation.bundle.Requirement;

@Documented
@Retention(CLASS)
@Target({ TYPE, PACKAGE })
@Requirement(
		namespace = EMFNamespaces.EMF_CONFIGURATOR_NAMESPACE,
		name = ResourceSetConfigurator.EMF_CONFIGURATOR_NAME,
		filter = "(" + EMFNamespaces.EMF_CONFIGURATOR_NAME + "=" + EMFRLangConstants.EMFRLANG_CAPABILITY_NAME + ")"
		)
@RequireEMF

/**
 * Meta annotation to generate a Require Capability for EMF R Language
 * 
 * @author Michal H. Siemaszko
 */
public @interface RequireEMFRLang {

}
