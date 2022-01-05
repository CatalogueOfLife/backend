package life.catalogue.es.ddl;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.ArrayUtils;

import static life.catalogue.es.ddl.ESDataType.KEYWORD;

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

  static boolean isA(Class<?> clazz, Class<?> interfaceOrSuper) {
    return interfaceOrSuper.isAssignableFrom(clazz);
  }

  static Class<?> getTypeArgument(Field f) {
    Preconditions.checkArgument(isA(f.getType(), Collection.class));
    Type t;
    try {
      t = f.getGenericType();
    } catch (TypeNotPresentException e) {
      throw new MappingException("Raw types not supported in Elasticsearch data model " + f.getName());
    }
    ParameterizedType pt = (ParameterizedType) t;
    Type[] args = pt.getActualTypeArguments();
    if (args.length != 1) { // Huh?
      throw new MappingException("Cannot map " + f.getName());
    }
    return (Class<?>) args[0];
  }

  static Class<?> getTypeArgument(Method m) {
    Preconditions.checkArgument(isA(m.getReturnType(), Collection.class));
    Type t;
    try {
      t = m.getGenericReturnType();
    } catch (TypeNotPresentException e) {
      throw new MappingException("Raw types not supported in Elasticsearch data model " + m.getName());
    }
    ParameterizedType pt = (ParameterizedType) t;
    Type[] args = pt.getActualTypeArguments();
    if (args.length != 1) { // Huh?
      throw new MappingException("Cannot map " + m.getName());
    }
    return (Class<?>) args[0];
  }

  private static void addMultiFields(AnnotatedElement fm, KeywordField kf) {
    Analyzers analyzers = fm.getAnnotation(Analyzers.class);
    if (analyzers != null) {
      Arrays.stream(analyzers.value()).filter(a -> a != Analyzer.KEYWORD).map(Analyzer::getMultiField).forEach(kf::addMultiField);
    }
  }

  /*
   * Stringy fields are always mapped to the "keyword" datatype (never "text"). However, if they are not analyzed using the KEYWORD
   * analyzer, values will not be indexed as-is. They will only be indexed using the other analyzers specified for the field and queries can
   * only target the "multifields" underneath the main field to access the indexed values. The main field becomes a ghostly hook for the
   * "multifields" underneath it (which is OK - it's all just syntax; it has no space or performance implications).
   */
  private static Boolean isIndexedField(AnnotatedElement fm, ESDataType esType) {
    if (esType == KEYWORD) {
      Analyzers annotation = fm.getAnnotation(Analyzers.class);
      if (annotation != null) {
        Analyzer[] anas = annotation.value();
        if (!ArrayUtils.contains(anas, Analyzer.KEYWORD)) {
          return Boolean.FALSE;
        }
      }
    }
    return fm.getAnnotation(NotIndexed.class) == null ? null : Boolean.FALSE;
  }

}
