package life.catalogue.es.mapping;

import static life.catalogue.es.mapping.ESDataType.KEYWORD;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class MappingUtil {

  static SimpleField createSimpleField(AnnotatedElement fm, ESDataType datatype) {
    Boolean indexed = isIndexedField(fm, datatype);
    SimpleField sf;
    if (datatype == KEYWORD) {
      sf = new KeywordField(indexed);
      addMultiFields(fm, (KeywordField) sf);
    } else if (datatype == ESDataType.DATE) {
      sf = new DateField(indexed);
    } else {
      sf = new SimpleField(datatype, indexed);
    }
    return sf;
  }

  static Set<Class<?>> newAncestor(Set<Class<?>> ancestors, Class<?> ancestor) {
    Set<Class<?>> set = new HashSet<>(ancestors);
    set.add(ancestor);
    return set;
  }

  static boolean isA(Class<?> cls, Class<?> interfaceOrSuper) {
    return interfaceOrSuper.isAssignableFrom(cls);
  }

  // Returns 1st type argument for generic type (e.g. Person for List<Person>)
  static Class<?> getClassForTypeArgument(Type t) {
    String s = t.getTypeName();
    int i = s.indexOf('<');
    s = s.substring(i + 1, s.length() - 1);
    try {
      return Class.forName(s);
    } catch (ClassNotFoundException e) {
      throw new MappingException(e);
    }
  }

  private static void addMultiFields(AnnotatedElement fm, KeywordField kf) {
    Analyzers analyzers = fm.getAnnotation(Analyzers.class);
    if (analyzers != null) {
      Arrays.stream(analyzers.value()).filter(a -> a != Analyzer.KEYWORD).map(Analyzer::getMultiField).forEach(kf::addMultiField);
    }
  }

  /*
   * With us stringy fields are always mapped to the KEYWORD datatype (never TEXT). However, if they are not analyzed using the KEYWORD
   * analyzer, they will not be indexed as-is. They will only be indexed using the other analyzers specified for the field and queries can
   * only target the "multifields" underneath the main field to access the indexed values. The main field becomes a ghostly hook for the
   * "multifields" underneath it (which is OK - it's all just syntax; it has no space or performance implications).
   */
  private static Boolean isIndexedField(AnnotatedElement fm, ESDataType esType) {
    if (esType == KEYWORD) {
      Analyzers annotation = fm.getAnnotation(Analyzers.class);
      if (annotation != null) {
        if (!Arrays.asList(annotation.value()).contains(Analyzer.KEYWORD)) {
          return Boolean.FALSE;
        }
      }
    }
    return fm.getAnnotation(NotIndexed.class) == null ? null : Boolean.FALSE;
  }

}
