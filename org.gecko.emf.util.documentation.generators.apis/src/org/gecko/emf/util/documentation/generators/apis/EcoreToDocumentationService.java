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
package org.gecko.emf.util.documentation.generators.apis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public abstract class EcoreToDocumentationService{

	public static final String ECORE_FILE_EXTENSION = ".ecore";
	protected static final Logger LOGGER = Logger.getLogger(EcoreToDocumentationService.class.getName());
	
	protected boolean canHandleFileFormat(String fileName) {
		Objects.requireNonNull(fileName, "File name cannot be null!");
		if(fileName.endsWith(ECORE_FILE_EXTENSION)) {
			return true;
		}
		return false;
	}
	
	protected abstract String getOutputFileExtension();
	protected abstract String getOutputFolder();
	protected abstract void doGenerateDocumentation(Path ecoreFilePath);
	
	public void ecoreToDocumentation(String ecoreFilePath) {
		Path path = Paths.get(ecoreFilePath);
		if(Files.isDirectory(path)) {
			try {
				Files.list(path).filter(p -> Files.isRegularFile(p)).forEach(p -> doGenerateDocumentation(p));
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "IOException while getting files from folder " + ecoreFilePath + " " + e.getMessage());
			}
			
		} else if(Files.isRegularFile(path)) {
			doGenerateDocumentation(path);
		}
	}
	
	

	protected void generateOutputFile(Path ecoreFilePath, CharSequence generatedCharSeq) {
		Path parentFolder = Paths.get(ecoreFilePath.toString()).getParent();
		String outputFileName = ecoreFilePath.getFileName().toString().replace(ECORE_FILE_EXTENSION, getOutputFileExtension());
		Path outputPath = Paths.get(parentFolder.toString(), getOutputFolder(), outputFileName);
		try {
			Files.deleteIfExists(outputPath);
			Files.createDirectories(outputPath.getParent());
			Files.createFile(outputPath);
			try(PrintWriter pw = new PrintWriter(outputPath.toFile())) {
				pw.write(generatedCharSeq.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}

}
