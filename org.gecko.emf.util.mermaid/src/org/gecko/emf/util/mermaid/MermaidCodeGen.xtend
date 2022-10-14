package org.gecko.emf.util.mermaid;

import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EEnum

class MermaidCodeGen {
	
	def toMermaidClassDiagram(EPackage epackage)
	'''
```mermaid 
classDiagram
«FOR eclassifier: epackage.EClassifiers»
«IF eclassifier instanceof EClass»
«" "»class «eclassifier.name» {
«toClassMembers(eclassifier as EClass)»
}
«"\n"»
«toClassReferences(eclassifier as EClass)»
«"\n"»
«IF !(eclassifier as EClass).ESuperTypes.isEmpty»
«toSuperTypes(eclassifier as EClass)»
«"\n"»
«ENDIF»
«ENDIF»
«ENDFOR»
«FOR eclassifier: epackage.EClassifiers»
«IF eclassifier instanceof EEnum»
«toEnumerator(eclassifier as EEnum)»
«"\n"»
«ENDIF»
«ENDFOR»
```
	'''
	
	def toClassMembers(EClass eclass)
	'''
«FOR attribute: eclass.EAttributes»
«" "»«attribute.EType.name» «attribute.name»
«ENDFOR»	
	'''
	
	def toClassReferences(EClass eclass)
	'''
«FOR ref: eclass.EReferences»
«" "»«eclass.name» «IF ref.isMany» «'\"*\"'»«ELSE»«'\"1\"'»«ENDIF»«' --> '» «'\"'»«ref.lowerBound»«'..'»«ref.upperBound»«'\"'» «ref.EType.name»«' : '»«ref.name»
«ENDFOR»	
	'''
	def toSuperTypes(EClass eclass)
	'''
«FOR parent: eclass.ESuperTypes»
«" "»«eclass.name» ..> «parent.name»
«ENDFOR»	
	'''
	
	def toEnumerator(EEnum enumerator)
	'''
«" "»class «enumerator.name» {
«" <<enumeration>>"»
«FOR value: enumerator.ELiterals»
«" "»«value.name»
«ENDFOR»
}
	'''

}