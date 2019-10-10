package org.col.es.mapping;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.col.es.mapping.ESDataType.NESTED;
import static org.col.es.mapping.MappingUtil.getMappedFields;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
public class FieldMappingsFactory extends MappingsFactory {

  protected void addFieldsToDocument(ComplexField document, Class<?> type, HashSet<Class<?>> ancestors) {
    Set<String> fields = new HashSet<>();
    for (Field field : getMappedFields(type)) {
      String fieldName = field.getName();
      fields.add(fieldName);
      ESField esField = createESField(field, ancestors);
      esField.setName(fieldName);
      document.addField(fieldName, esField);
    }
  }

  private ESField createESField(Field field, HashSet<Class<?>> ancestors) {
    ESDataType esType;
    MapToType annotation = field.getAnnotation(MapToType.class);
    if (annotation != null) {
      esType = annotation.value();
    } else {
      Class<?> mapToType = mapType(field.getType(), field.getGenericType());
      esType = DataTypeMap.INSTANCE.getESType(mapToType);
      if (esType == null) { // we are dealing with a nested object
        if (ancestors.contains(mapToType)) { // Circular class composition, just too complicated
          throw new ClassCircularityException(field, mapToType);
        }
        return createDocument(field, mapToType, newTree(ancestors, mapToType));
      }
    }
    return createSimpleField(field, esType);
  }

  private ComplexField createDocument(Field field, Class<?> mapToType, HashSet<Class<?>> ancestors) {
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

}
