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
package org.gecko.emf.jakartars.annotations;

/**
 * Triggers check for Empty Content
 * @author Jürgen Albert
 * @since 06.11.2014 
 */
public @interface ContentNotEmpty {
	
	String message() default "Empty content not allowed"; 

}
