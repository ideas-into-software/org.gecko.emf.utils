package org.gecko.emf.util.documentation.generators.mermaid;

import java.util.List
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.common.util.EMap
import org.eclipse.emf.ecore.EAnnotation
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EEnumLiteral
import org.eclipse.emf.ecore.EModelElement
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EStructuralFeature

class MarkdownCodeGen {
	
	MermaidCodeGen mermaidCodeGen = new MermaidCodeGen();
	
	def generateMarkdownDoc(EPackage epackage)
	'''
# «epackage.name»
«"\n"»
## Class Diagram
«"\n"»
«mermaidCodeGen.toMermaidClassDiagram(epackage)»
«"\n"»
«toEClassifierOverview(epackage.EClassifiers)»
	'''
	
	def toEClassifierOverview(EList<EClassifier> eclassifiers) {
var List<EClass> classes = eclassifiers.filter[ec | ec instanceof EClass].map[ec | ec as EClass].toList;	
var List<EEnum> enums = eclassifiers.filter[ec | ec instanceof EEnum].map[ec | ec as EEnum].toList;	
'''
## Classes Overview
«"\n"»
«FOR eclass: classes»
«toClassOverview(eclass)»
«"\n"»
«"\n"»
«ENDFOR»
«"\n"»
## Enumerators Overview
«"\n"»
«FOR enumerator: enums»
«toEnumOverview(enumerator)»
«"\n"»
«"\n"»
«ENDFOR»
'''
}
	
	
	def toEnumOverview(EEnum enumerator)
	'''
### «enumerator.name»
«"\n"»
#### Description
«"\n"»
«toModelElementDescription(enumerator)»
«"\n"»
#### Literals
«"\n"»
«toLiteralOverview(enumerator.ELiterals)»
	'''	

	def toLiteralOverview(EList<EEnumLiteral> literals) 
	'''
«IF literals.empty»
None.
«ELSE»
| Literal| Description|
| -----| -----------|
«FOR literal: literals»
|«literal.name»|«toModelElementDescription(literal)»|
«ENDFOR»
«ENDIF»
	'''

	def toClassOverview(EClass eclass)
	'''
### «eclass.name»
«"\n"»
#### Description
«"\n"»
«toModelElementDescription(eclass)»
«"\n"»
#### Class Diagram
«"\n"»
«mermaidCodeGen.startMermaidClassDiagram»
«mermaidCodeGen.toClassDiagram(eclass)»
«mermaidCodeGen.endMermaidClassDiagram»
«"\n"»
#### Fields
«"\n"»
«toClassStructuralFeaturesDescription(eclass.EAttributes)»
«"\n"»
#### References
«"\n"»
«toClassStructuralFeaturesDescription(eclass.EReferences)»
«"\n"»
	'''
	
	
		def <T extends EStructuralFeature> toClassStructuralFeaturesDescription(EList<T> features) {
		'''
«IF features.empty»
None.
«ELSE»
| Name| Type| Bounds| Description|
| -----| ----| ------| -----------|
«FOR feature: features»
|«feature.name»|«feature.EType.name»|«feature.lowerBound»..«feature.upperBound»|«toModelElementDescription(feature)»|
«ENDFOR»
«ENDIF»
	'''	
		}
	
	
	def String toModelElementDescription(EModelElement element) {
		for(EAnnotation annotation : element.EAnnotations) {
			var EMap<String, String> details = annotation.details;
			if(details.containsKey("documentation")) {
				return details.get("documentation").trim;
			}
			
		}	
		return "None."	
	}
	
}