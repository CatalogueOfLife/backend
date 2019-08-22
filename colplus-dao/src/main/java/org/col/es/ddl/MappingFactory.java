package org.col.es.ddl;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.col.es.ddl.ESDataType.KEYWORD;
import static org.col.es.ddl.ESDataType.NESTED;
import static org.col.es.ddl.MappingUtil.getClassForTypeArgument;
import static org.col.es.ddl.MappingUtil.getFieldName;
import static org.col.es.ddl.MappingUtil.getMappedProperties;
import static org.col.es.ddl.MultiField.AUTO_COMPLETE;
import static org.col.es.ddl.MultiField.DEFAULT;
import static org.col.es.ddl.MultiField.IGNORE_CASE;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
public class MappingFactory<T> {

  /*
   * Cache of document type mappings. Since we currently have just one document type (EsNameUsage), it will contain just one entry.
   */
  private static final HashMap<Class<?>, DocumentTypeMapping> cache = new HashMap<>();

  /*
   * Whether to map enums to the integer or to the keyword datatype. This is more or less separate from how you serialize enums. Obviously
   * when serializing enums to strings, you have no choice but to use the keyword datatype. But when serializing enums to integers, you have
   * the choice of storing them as strings or as integers, and there can be good reasons to store them as strings (as explained in the
   * Elasticsearch performance tuning guide). Specifying the datatype mapping here saves you from having to decorate each and every enum in
   * the data model with the @MapToType annotation. You can still use the @MapToType to override the global behaviour.
   */
  private boolean mapEnumToInt = true;

  /**
   * Creates a document type mapping for the specified class name.
   *
   * @param className
   * @return
   */
  @SuppressWarnings("unchecked")
  public DocumentTypeMapping getMapping(String className) {
    try {
      return getMapping((Class<T>) Class.forName(className));
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
  public DocumentTypeMapping getMapping(Class<T> type) {
    DocumentTypeMapping mapping = cache.get(type);
    if (mapping == null) {
      mapping = new DocumentTypeMapping();
      addFieldsToDocument(mapping, type, newTree(new HashSet<>(0), type));
      cache.put(type, mapping);
    }
    return mapping;
  }

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
   * Maps a Java class to another Java class that must be used in its place. The latter class is then mapped to an Elasticsearch data type.
   * When mapping arrays, it's not the array that is mapped but the class of its elements. No array type exists or is required in
   * Elasticsearch. Fields are intrinsically multi-valued.
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
