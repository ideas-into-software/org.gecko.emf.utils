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
package org.gecko.emf.util.mermaid.command;

import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.util.mermaid.MermaidCodeGen;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service=EcoreToMermaidCommandComponent.class, property= {
        "osgi.command.scope=ecoretomermaid",
        "osgi.command.function=ecoreToMermaid"
        })
public class EcoreToMermaidCommandComponent {
	
	@Reference
	ResourceSet resourceSet;
	
	private static final String BASE_PATH = System.getProperty("base.path");
	private static final String DATA_FOLDER = BASE_PATH + "/data/";

	public void ecoreToMermaid(String ecoreFileName) {
		Resource resource = resourceSet.createResource(URI.createFileURI(DATA_FOLDER + ecoreFileName));
		try {
			resource.load(null);
			if(resource.getContents().isEmpty()) {
				System.err.println("Resource has no contents!");
				return;
			}
			EObject eObj = resource.getContents().get(0);
			if(eObj instanceof EPackage) {
				EPackage ePackage = (EPackage) eObj;
				MermaidCodeGen mermaidCodeGen = new MermaidCodeGen();
				CharSequence cs = mermaidCodeGen.toMermaidClassDiagram(ePackage);
				String outputFile = DATA_FOLDER + ecoreFileName.replace("ecore", "md");
				try(PrintWriter pw = new PrintWriter(outputFile)) {
					pw.write(cs.toString());
				}
			}
			else {
				System.err.println("Content is not an EClass!");
				return;
			}
		} catch(IOException e) {
			System.err.println("IOException! " + e.getMessage());
			return;
		}		
	}
}
