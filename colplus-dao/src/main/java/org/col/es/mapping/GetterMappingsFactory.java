package org.col.es.mapping;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.col.es.mapping.ESDataType.NESTED;
import static org.col.es.mapping.MappingUtil.getFieldName;
import static org.col.es.mapping.MappingUtil.getMappedProperties;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
class GetterMappingsFactory extends MappingsFactory {

  protected void addFieldsToDocument(ComplexField document, Class<?> type, HashSet<Class<?>> ancestors) {
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
        if (ancestors.contains(mapToType)) { // Circular class composition, just too complicated
          throw new ClassCircularityException(method, mapToType);
        }
        return createDocument(method, mapToType, newTree(ancestors, mapToType));
      }
    }
    return createSimpleField(method, esType);
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

}
