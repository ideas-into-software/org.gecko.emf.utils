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
package org.gecko.emf.util.documentation.generators.mermaid.component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService;
import org.gecko.emf.util.documentation.generators.mermaid.MarkdownCodeGen;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(name="EcoreToMermaidComponent", service=EcoreToDocumentationService.class)
public class EcoreToMermaidComponent extends EcoreToDocumentationService{

	@Reference
	ResourceSet resourceSet;

	private static final String MD_FILE_EXTENSION = ".md";
	private static final String MD_FOLDER_NAME = "md";
	private static final Logger LOGGER = Logger.getLogger(EcoreToMermaidComponent.class.getName());

	public void ecoreToMermaid(String ecoreFilePath) {
		super.ecoreToDocumentation(ecoreFilePath);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService#doGenerateDocumentation(java.nio.file.Path)
	 */
	@Override
	protected void doGenerateDocumentation(Path ecoreFilePath) {
		if(!canHandleFileFormat(ecoreFilePath.getFileName().toString())) {
			LOGGER.log(Level.WARNING, "Cannot handle file format for " + ecoreFilePath);
			return;
		}
		LOGGER.fine("Processing file " + ecoreFilePath);
		EPackage ePackage = extractEPackageFromEcore(ecoreFilePath);
		MarkdownCodeGen markdownCodeGen = new MarkdownCodeGen();
		CharSequence cs = markdownCodeGen.generateMarkdownDoc(ePackage);
		generateOutputFile(ecoreFilePath, cs);
	}

	private EPackage extractEPackageFromEcore(Path ecoreFilePath) {
		Resource resource = resourceSet.createResource(URI.createFileURI(ecoreFilePath.toString()));
		try {
			resource.load(null);
			if(resource.getContents().isEmpty()) {
				LOGGER.log(Level.SEVERE, "Resource has no contents!");
				return null;
			}
			EObject eObj = resource.getContents().get(0);
			if(eObj instanceof EPackage) {
				return(EPackage) eObj;
			}
			else {
				LOGGER.log(Level.SEVERE, "Content is not an EPackage!");
				return null;
			}
		} catch(IOException e) {
			LOGGER.log(Level.SEVERE, "IOException in file " + ecoreFilePath + " " + e.getMessage());
			return null;
		}		
	}



	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService#getOutputFileExtension()
	 */
	@Override
	protected String getOutputFileExtension() {
		return MD_FILE_EXTENSION;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService#getOutputFolder()
	 */
	@Override
	protected String getOutputFolder() {
		return MD_FOLDER_NAME;
	}


}
