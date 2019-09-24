package org.col.es.mapping;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.col.es.mapping.ESDataType.KEYWORD;
import static org.col.es.mapping.ESDataType.NESTED;
import static org.col.es.mapping.MappingUtil.getClassForTypeArgument;
import static org.col.es.mapping.MappingUtil.getFieldName;
import static org.col.es.mapping.MappingUtil.getMappedProperties;
import static org.col.es.mapping.MultiField.AUTO_COMPLETE;
import static org.col.es.mapping.MultiField.DEFAULT;
import static org.col.es.mapping.MultiField.IGNORE_CASE;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
public class MappingsFactory {

  private boolean mapEnumToInt = true;

  /**
   * Creates a document type mapping for the specified class name.
   *
   * @param className
   * @return
   */
  public Mappings getMapping(String className) {
    try {
      return getMapping(Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new MappingException("No such class: " + className);
    }
  }

  /**
   * Creates a document type mapping for the specified class.
   *
   * @param type
   * @return
   */
  public Mappings getMapping(Class<?> type) {
    Mappings mappings = new Mappings();
    addFieldsToDocument(mappings, type, newTree(new HashSet<>(0), type));
    return mappings;
  }

  /**
   * Whether to map enums to Elasticsearch's integer datatype or to the keyword datatype. Default true. This is more or
   * less separate from how you **serialize** enums. Obviously when serializing enums to strings, you have no choice but
   * to use the keyword datatype. But when serializing enums to integers, you still have the choice of storing them as
   * strings or as integers. There can be good reasons to store them as strings (see Elasticsearch performance tuning
   * guide). Specifying the datatype mapping here saves you from having to decorate each and every enum in the data model
   * with the @MapToType annotation. You can still use the @MapToType to override the global behaviour. Note that in
   * EsModule we specify that we want enums to be serialized using their ordinal value, so we are indeed free to choose
   * between the keyword and integer datatype.
   */
  public boolean isMapEnumToInt() {
    return mapEnumToInt;
  }

  public void setMapEnumToInt(boolean mapEnumToInt) {
    this.mapEnumToInt = mapEnumToInt;
  }

  private void addFieldsToDocument(ComplexField document, Class<?> type, HashSet<Class<?>> ancestors) {
    Set<String> fields = new HashSet<>();
    for (Method method : getMappedProperties(type)) {
      String fieldName = getFieldName(method);
      fields.add(fieldName);
      ESField esField = createESField(method, ancestors);
      esField.setName(fieldName);
      document.addField(fieldName, esField);
    }
  }

  private ESField createESField(Method method, HashSet<Class<?>> ancestors) {
    ESDataType esType;
    MapToType annotation = method.getAnnotation(MapToType.class);
    if (annotation != null) {
      esType = annotation.value();
    } else {
      Class<?> mapToType = mapType(method.getReturnType(), method.getGenericReturnType());
      esType = DataTypeMap.INSTANCE.getESType(mapToType);
      if (esType == null) { // we are dealing with a nested object
        if (ancestors.contains(mapToType)) {
          throw new ClassCircularityException(method, mapToType);
        }
        return createDocument(method, mapToType, newTree(ancestors, mapToType));
      }
    }
    return createSimpleField(method, esType);
  }

  private static SimpleField createSimpleField(AnnotatedElement fm, ESDataType datatype) {
    Boolean indexed = isIndexedField(fm, datatype);
    SimpleField sf;
    if (datatype == KEYWORD) {
      sf = new KeywordField(indexed);
      addMultiFields((KeywordField) sf, fm);
    } else if (datatype == ESDataType.DATE) {
      sf = new DateField(indexed);
    } else {
      sf = new SimpleField(datatype, indexed);
    }
    return sf;
  }

  private ComplexField createDocument(Method method, Class<?> mapToType, HashSet<Class<?>> ancestors) {
    Class<?> realType = method.getReturnType();
    ComplexField document;
    if (realType.isArray() || isA(realType, Collection.class)) {
      document = new ComplexField(NESTED);
    } else {
      document = new ComplexField();
    }
    addFieldsToDocument(document, mapToType, ancestors);
    return document;
  }

  private static void addMultiFields(KeywordField kf, AnnotatedElement fm) {
    Analyzers annotation = fm.getAnnotation(Analyzers.class);
    if (annotation != null && annotation.value().length != 0) {
      List<Analyzer> analyzers = Arrays.asList(annotation.value());
      for (Analyzer a : analyzers) {
        switch (a) {
          case IGNORE_CASE:
            kf.addMultiField(IGNORE_CASE);
            break;
          case DEFAULT:
            kf.addMultiField(DEFAULT);
            break;
          case AUTO_COMPLETE:
            kf.addMultiField(AUTO_COMPLETE);
            break;
          case KEYWORD:
          default:
            break;
        }
      }
    }
  }

  private static Boolean isIndexedField(AnnotatedElement fm, ESDataType esType) {
    if (esType == KEYWORD) {
      /*
       * String fields always have datatype KEYWORD. However, if they are not analyzed by the (no-op) KEYWORD analyzer, but
       * they __are__ analyzed using one or more other analyzers, the field will not be indexed as-is. It will only be indexed
       * using the other analyzers, and queries must target the "multi fields" underneath the main
       * field to access the indexed values. 
       */
      Analyzers annotation = fm.getAnnotation(Analyzers.class);
      if (annotation != null) {
        if (!Arrays.asList(annotation.value()).contains(Analyzer.KEYWORD)) {
          return Boolean.FALSE;
        }
      }
    }
    return fm.getAnnotation(NotIndexed.class) == null ? null : Boolean.FALSE;
  }

  /*
   * Maps a Java class to another Java class that must be used in its place. The latter class is then mapped to an
   * Elasticsearch data type. When mapping arrays, it's not the array that is mapped but the class of its elements. No
   * array type exists or is required in Elasticsearch. Fields are intrinsically multi-valued.
   */
  private Class<?> mapType(Class<?> type, Type typeArg) {
    if (type.isArray()) {
      return mapType(type.getComponentType(), null);
    }
    if (Collection.class.isAssignableFrom(type)) {
      return mapType(getClassForTypeArgument(typeArg), null);
    }
    if (type.isEnum()) {
      return mapEnumToInt ? int.class : String.class;
    }
    return type;
  }

  private static HashSet<Class<?>> newTree(HashSet<Class<?>> ancestors, Class<?> newType) {
    HashSet<Class<?>> set = new HashSet<>();
    set.addAll(ancestors);
    set.add(newType);
    return set;
  }

  private static boolean isA(Class<?> cls, Class<?> interfaceOrSuperClass) {
    return interfaceOrSuperClass.isAssignableFrom(cls);
  }

}
