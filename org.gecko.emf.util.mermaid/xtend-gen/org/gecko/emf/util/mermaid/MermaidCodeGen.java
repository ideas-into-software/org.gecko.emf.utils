package org.gecko.emf.util.mermaid;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtend2.lib.StringConcatenation;

@SuppressWarnings("all")
public class MermaidCodeGen {
  public CharSequence toMermaidClassDiagram(final EPackage epackage) {
    StringConcatenation _builder = new StringConcatenation();
    _builder.append("```mermaid ");
    _builder.newLine();
    _builder.append("classDiagram");
    _builder.newLine();
    {
      EList<EClassifier> _eClassifiers = epackage.getEClassifiers();
      for(final EClassifier eclassifier : _eClassifiers) {
        {
          if ((eclassifier instanceof EClass)) {
            _builder.append(" ");
            _builder.append("class ");
            String _name = ((EClass)eclassifier).getName();
            _builder.append(_name);
            _builder.append(" {");
            _builder.newLineIfNotEmpty();
            CharSequence _classMembers = this.toClassMembers(((EClass) eclassifier));
            _builder.append(_classMembers);
            _builder.newLineIfNotEmpty();
            _builder.append("}");
            _builder.newLine();
            _builder.append("\n");
            _builder.newLineIfNotEmpty();
            CharSequence _classReferences = this.toClassReferences(((EClass) eclassifier));
            _builder.append(_classReferences);
            _builder.newLineIfNotEmpty();
            _builder.append("\n");
            _builder.newLineIfNotEmpty();
            {
              boolean _isEmpty = ((EClass) eclassifier).getESuperTypes().isEmpty();
              boolean _not = (!_isEmpty);
              if (_not) {
                CharSequence _superTypes = this.toSuperTypes(((EClass) eclassifier));
                _builder.append(_superTypes);
                _builder.newLineIfNotEmpty();
                _builder.append("\n");
                _builder.newLineIfNotEmpty();
              }
            }
          }
        }
      }
    }
    {
      EList<EClassifier> _eClassifiers_1 = epackage.getEClassifiers();
      for(final EClassifier eclassifier_1 : _eClassifiers_1) {
        {
          if ((eclassifier_1 instanceof EEnum)) {
            CharSequence _enumerator = this.toEnumerator(((EEnum) eclassifier_1));
            _builder.append(_enumerator);
            _builder.newLineIfNotEmpty();
            _builder.append("\n");
            _builder.newLineIfNotEmpty();
          }
        }
      }
    }
    _builder.append("```");
    _builder.newLine();
    return _builder;
  }

  public CharSequence toClassMembers(final EClass eclass) {
    StringConcatenation _builder = new StringConcatenation();
    {
      EList<EAttribute> _eAttributes = eclass.getEAttributes();
      for(final EAttribute attribute : _eAttributes) {
        _builder.append(" ");
        String _name = attribute.getEType().getName();
        _builder.append(_name);
        _builder.append(" ");
        String _name_1 = attribute.getName();
        _builder.append(_name_1);
        _builder.newLineIfNotEmpty();
      }
    }
    return _builder;
  }

  public CharSequence toClassReferences(final EClass eclass) {
    StringConcatenation _builder = new StringConcatenation();
    {
      EList<EReference> _eReferences = eclass.getEReferences();
      for(final EReference ref : _eReferences) {
        _builder.append(" ");
        String _name = eclass.getName();
        _builder.append(_name);
        _builder.append(" ");
        {
          boolean _isMany = ref.isMany();
          if (_isMany) {
            _builder.append(" ");
            _builder.append("\"*\"");
          } else {
            _builder.append("\"1\"");
          }
        }
        _builder.append(" --> ");
        _builder.append(" ");
        _builder.append("\"");
        int _lowerBound = ref.getLowerBound();
        _builder.append(_lowerBound);
        _builder.append("..");
        int _upperBound = ref.getUpperBound();
        _builder.append(_upperBound);
        _builder.append("\"");
        _builder.append(" ");
        String _name_1 = ref.getEType().getName();
        _builder.append(_name_1);
        _builder.append(" : ");
        String _name_2 = ref.getName();
        _builder.append(_name_2);
        _builder.newLineIfNotEmpty();
      }
    }
    return _builder;
  }

  public CharSequence toSuperTypes(final EClass eclass) {
    StringConcatenation _builder = new StringConcatenation();
    {
      EList<EClass> _eSuperTypes = eclass.getESuperTypes();
      for(final EClass parent : _eSuperTypes) {
        _builder.append(" ");
        String _name = eclass.getName();
        _builder.append(_name);
        _builder.append(" ..> ");
        String _name_1 = parent.getName();
        _builder.append(_name_1);
        _builder.newLineIfNotEmpty();
      }
    }
    return _builder;
  }

  public CharSequence toEnumerator(final EEnum enumerator) {
    StringConcatenation _builder = new StringConcatenation();
    _builder.append(" ");
    _builder.append("class ");
    String _name = enumerator.getName();
    _builder.append(_name);
    _builder.append(" {");
    _builder.newLineIfNotEmpty();
    _builder.append(" <<enumeration>>");
    _builder.newLineIfNotEmpty();
    {
      EList<EEnumLiteral> _eLiterals = enumerator.getELiterals();
      for(final EEnumLiteral value : _eLiterals) {
        _builder.append(" ");
        String _name_1 = value.getName();
        _builder.append(_name_1);
        _builder.newLineIfNotEmpty();
      }
    }
    _builder.append("}");
    _builder.newLine();
    return _builder;
  }
}
