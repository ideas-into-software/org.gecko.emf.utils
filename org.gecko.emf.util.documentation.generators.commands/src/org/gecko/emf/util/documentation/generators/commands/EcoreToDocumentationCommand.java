/**
 * Copyright (c) 2012 - 2018 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.emf.util.documentation.generators.commands;

import org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;



@Component(name = "EcoreToDocumentationCommand", service=EcoreToDocumentationCommand.class, property= {
		"osgi.command.scope=EcoreToDocumentation",
		"osgi.command.function=ecoreToPlantuml",
		"osgi.command.function=ecoreToMermaid"
})
public class EcoreToDocumentationCommand {

	@Reference(target = "(component.name=EcoreToPlantumlComponent)")
	EcoreToDocumentationService ecoreToPlantumlComponent;
	
	@Reference(target = "(component.name=EcoreToMermaidComponent)")
	EcoreToDocumentationService ecoreToMermaidComponent;

	
	public void ecoreToPlantuml(String ecoreFilePath) {
		ecoreToPlantumlComponent.ecoreToDocumentation(ecoreFilePath);
	}
	
	public void ecoreToMermaid(String ecoreFilePath) {
		ecoreToMermaidComponent.ecoreToDocumentation(ecoreFilePath);
	}
}
