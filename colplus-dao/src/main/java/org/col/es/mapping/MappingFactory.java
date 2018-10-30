package org.col.es.mapping;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.col.es.annotations.Analyzer;
import org.col.es.annotations.Analyzers;
import org.col.es.annotations.NotIndexed;

import static org.col.es.mapping.ESDataType.KEYWORD;
import static org.col.es.mapping.ESDataType.NESTED;
import static org.col.es.mapping.MappingUtil.extractProperty;
import static org.col.es.mapping.MappingUtil.getClassForTypeArgument;
import static org.col.es.mapping.MappingUtil.getFields;
import static org.col.es.mapping.MappingUtil.getMappedProperties;
import static org.col.es.mapping.MultiField.AUTO_COMPLETE;
import static org.col.es.mapping.MultiField.DEFAULT;
import static org.col.es.mapping.MultiField.IGNORE_CASE;
import static org.col.es.mapping.MultiField.NGRAM;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
public class MappingFactory<T> {

  // Cache of document type mappings. Since we really have only one document type (EsNameUsage), it
  // will contain just one entry.
  private static final HashMap<Class<?>, Mapping<?>> cache = new HashMap<>();

  private boolean mapEnumToInt;

  /**
   * Creates a document type mapping for the specified class name.
   * 
   * @param className
   * @return
   */
  @SuppressWarnings("unchecked")
  public Mapping<T> getMapping(String className) {
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
  public Mapping<T> getMapping(Class<T> type) {
    @SuppressWarnings("unchecked")
    Mapping<T> mapping = (Mapping<T>) cache.get(type);
    if (mapping == null) {
      mapping = new Mapping<>(type);
      addFieldsToDocument(mapping, type, newTree(new HashSet<>(0), type));
      cache.put(type, mapping);
    }
    return mapping;
  }

  public boolean isMapEnumToInt() {
    return mapEnumToInt;
  }

  public void setMapEnumToInt(boolean soreEnumAsInt) {
    this.mapEnumToInt = soreEnumAsInt;
  }

  private void addFieldsToDocument(ComplexField document, Class<?> type,
      HashSet<Class<?>> ancestors) {
    Set<String> fields = new HashSet<>();
    for (Method javaMethod : getMappedProperties(type)) {
      // System.out.println("Mapping method " + javaMethod);
      String methodName = javaMethod.getName();
      String fieldName = extractProperty(methodName);
      fields.add(fieldName);
      ESField esField = createESField(javaMethod, ancestors);
      esField.setName(fieldName);
      document.addField(fieldName, esField);
    }
    for (Field javaField : getFields(type)) {
      if (fields.contains(javaField.getName())) {
        continue;
      }
      // System.out.println("Mapping field " + javaField.getName());
      ESField esField = createESField(javaField, ancestors);
      esField.setName(javaField.getName());
      document.addField(javaField.getName(), esField);
    }
  }

  private ESField createESField(Method method, HashSet<Class<?>> ancestors) {
    Class<?> realType = method.getReturnType();
    Class<?> mapToType = mapType(realType, method.getGenericReturnType());
    ESDataType esType = DataTypeMap.INSTANCE.getESType(mapToType);
    if (esType == null) {
      /*
       * Then the Java type does not map to a simple Elasticsearch type; the Elasticsearch type is
       * either "object" or "nested".
       */
      if (ancestors.contains(mapToType)) {
        throw new ClassCircularityException(method, mapToType);
      }
      return createDocument(method, mapToType, newTree(ancestors, mapToType));
    }
    return createSimpleField(method, esType);
  }

  private ESField createESField(Field field, HashSet<Class<?>> ancestors) {
    Class<?> realType = field.getType();
    Class<?> mapToType = mapType(realType, field.getGenericType());
    ESDataType esType = DataTypeMap.INSTANCE.getESType(mapToType);
    if (esType == null) {
      if (ancestors.contains(mapToType)) {
        throw new ClassCircularityException(field, mapToType);
      }
      return createDocument(field, mapToType, newTree(ancestors, mapToType));
    }
    return createSimpleField(field, esType);
  }

  private static SimpleField createSimpleField(AnnotatedElement fm, ESDataType esType) {
    SimpleField sf;
    switch (esType) {
      case KEYWORD:
        sf = new KeywordField();
        break;
      case DATE:
        sf = new DateField();
        break;
      default:
        sf = new SimpleField(esType);
    }
    if (fm.getAnnotation(NotIndexed.class) == null) {
      if (esType == KEYWORD) {
        addMultiFields((KeywordField) sf, fm);
      }
    } else {
      sf.setIndex(Boolean.FALSE);
    }
    return sf;
  }

  private ComplexField createDocument(Field field, Class<?> mapToType,
      HashSet<Class<?>> ancestors) {
    Class<?> realType = field.getType();
    ComplexField document;
    if (realType.isArray() || isA(realType, Collection.class)) {
      document = new ComplexField(NESTED);
    } else {
      document = new ComplexField();
    }
    addFieldsToDocument(document, mapToType, ancestors);
    return document;
  }

  private ComplexField createDocument(Method method, Class<?> mapToType,
      HashSet<Class<?>> ancestors) {
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

  /*
   * Returns false if the Java field does not contain the @Analyzers annotation. Otherwise it
   * returns true.
   */
  private static boolean addMultiFields(KeywordField kf, AnnotatedElement fm) {
    Analyzers annotation = fm.getAnnotation(Analyzers.class);
    if (annotation == null) {
      return false;
    }
    if (annotation.value().length == 0) {
      return true;
    }
    List<Analyzer> value = Arrays.asList(annotation.value());
    EnumSet<Analyzer> analyzers = EnumSet.copyOf(value);
    for (Analyzer a : analyzers) {
      switch (a) {
        case IGNORE_CASE:
          kf.addMultiField(IGNORE_CASE);
          break;
        case DEFAULT:
          kf.addMultiField(DEFAULT);
          break;
        case NGRAM:
          kf.addMultiField(NGRAM);
          break;
        case AUTO_COMPLETE:
          kf.addMultiField(AUTO_COMPLETE);
          break;
        default:
          break;
      }
    }
    return true;
  }

  /*
   * Maps a Java class to another Java class that must be used in its place. The latter class is
   * then mapped to an Elasticsearch data type. When mapping arrays, it's not the array that is
   * mapped but the class of its elements. No array type exists or is required in Elasticsearch.
   * Fields are intrinsically multi-valued.
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
