package org.gecko.emf.util.documentation.generators.plantuml;

import java.util.List
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EReference

class PlantumlCodeGen {
	def toPlantumlClassDiagram(EPackage epackage)
	'''
«startPlantumlClassDiagram()»
«toEClassifierDiagram(epackage.EClassifiers)»
«endPlantumlClassDiagram()»
	'''
	
	def startPlantumlClassDiagram()
	'''
@startuml
	'''
	
	def endPlantumlClassDiagram()
	'''
@enduml
	'''
	
	
	def toEClassifierDiagram(EList<EClassifier> eclassifiers) {
var List<EClass> classes = eclassifiers.filter[ec | ec instanceof EClass].map[ec | ec as EClass].toList;	
var List<EEnum> enums = eclassifiers.filter[ec | ec instanceof EEnum].map[ec | ec as EEnum].toList;	
'''
«FOR eclass: classes»
«toClassDiagram(eclass)»
«"\n"»
«"\n"»
«ENDFOR»
«FOR enumerator: enums»
«toEnumerator(enumerator)»
«"\n"»
«"\n"»
«ENDFOR»
'''
}
	
	def toClassDiagram(EClass eclass) 
	'''
«" "»class «eclass.name» {
«toClassMembers(eclass as EClass)»
}
«"\n"»
«toClassReferences(eclass as EClass)»
«"\n"»
«IF !(eclass as EClass).ESuperTypes.isEmpty»
«toSuperTypes(eclass as EClass)»
«"\n"»
«ENDIF»	
	'''
	
	def toClassMembers(EClass eclass)
	'''
«FOR attribute: eclass.EAttributes»
«" "»«attribute.name» : «attribute.EType.name» 
«ENDFOR»	
	'''
	
	def toClassReferences(EClass eclass)
	'''
«FOR ref: eclass.EReferences»
«toClassRef(eclass, ref)»
«ENDFOR»
	'''

	def toClassRef(EClass eclass, EReference ref)  {
		var multiplicity = extractMultiplicity(ref.lowerBound, ref.upperBound)
		var refType = extractRefType(ref, eclass)
		var isInRefModel = isInRefModel(ref, eclass)
		'''
«IF isInRefModel»
«" "»«eclass.name» «' .. '» «'\"'»«multiplicity»«'\"'» «refType»«' : '»«ref.name»
«ELSE»
«" "»«eclass.name» «' --> '» «'\"'»«multiplicity»«'\"'» «refType»«' : '»«ref.name»
«ENDIF»	
		'''
	}
	
	def String extractRefType(EReference ref, EClass containerClass) {
		if(ref.EType.EPackage.name === containerClass.EPackage.name) {
			return ref.EType.name
		} else {
//			TODO this should be the correct form when linking to a reference model (https://github.com/mermaid-js/mermaid/issues/1052)
//			return ref.EType.EPackage.nsURI + "#//" + ref.EType.name;
			return ref.EType.name
		}
	}
	
	def boolean isInRefModel(EReference ref, EClass containerClass) {
		if(ref.EType.EPackage.name === containerClass.EPackage.name) {
			return false
		} else {
			return true
		}
	}

	def String extractMultiplicity(int lowerBound, int upperBound) {
		if(lowerBound == 0 && upperBound == -1) {
			return "*"
		}		
		else if(lowerBound == 1 && upperBound == -1) {
			return "1..*"
		}
		return ((String.valueOf(lowerBound) + ".." +  (String.valueOf(upperBound))))
	}



	def toSuperTypes(EClass eclass)
	'''
«FOR parent: eclass.ESuperTypes»
«" "»«eclass.name» ..> «parent.name»
«ENDFOR»	
	'''
	
	def toEnumerator(EEnum enumerator)
	'''
«" "»enum «enumerator.name» {
«FOR value: enumerator.ELiterals»
«" "»«value.name»
«ENDFOR»
}
	'''
}