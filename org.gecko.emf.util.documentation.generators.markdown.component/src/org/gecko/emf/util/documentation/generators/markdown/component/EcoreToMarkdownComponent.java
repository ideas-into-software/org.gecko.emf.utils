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
package org.gecko.emf.util.documentation.generators.markdown.component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationConstants;
import org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService;
import org.gecko.emf.util.documentation.generators.markdown.MarkdownCodeGen;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(name="EcoreToMarkdownComponent", service=EcoreToDocumentationService.class)
public class EcoreToMarkdownComponent implements EcoreToDocumentationService{

	@Reference
	ResourceSet resourceSet;

	private static final String MD_FILE_EXTENSION = ".md";
	private String MD_FOLDER_NAME = "md";
	private static final Logger LOGGER = Logger.getLogger(EcoreToMarkdownComponent.class.getName());

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService#doGenerateDocumentation(java.nio.file.Path)
	 */
	@Override
	public void doGenerateDocumentation(Path ecoreFilePath, EcoreToDocumentationConstants mode) {
		if(!canHandleFileFormat(ecoreFilePath.getFileName().toString())) {
			LOGGER.log(Level.WARNING, "Cannot handle file format for " + ecoreFilePath);
			return;
		}
		LOGGER.fine("Processing file " + ecoreFilePath);
		EPackage ePackage = extractEPackageFromEcore(ecoreFilePath);
		MarkdownCodeGen markdownCodeGen = new MarkdownCodeGen();
		CharSequence cs = markdownCodeGen.generateMarkdownDoc(ePackage, mode);
		generateOutputFile(ecoreFilePath, cs, mode);
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
	public String getOutputFileExtension() {
		return MD_FILE_EXTENSION;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.gecko.emf.util.documentation.generators.apis.EcoreToDocumentationService#getOutputFolder()
	 */
	@Override
	public String getOutputFolder(EcoreToDocumentationConstants mode) {
		switch(mode) {
		case CLASS_DIAGRAM_GEN_MERMAID_OPTION:
			return MD_FOLDER_NAME + "_mermaid";
		case CLASS_DIAGRAM_GEN_PLANTUML_OPTION:
			return MD_FOLDER_NAME + "_plantuml";
		default:
			return MD_FOLDER_NAME;
		}		
	}


}
