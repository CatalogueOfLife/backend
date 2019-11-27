package life.catalogue.es.mapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static java.lang.reflect.Modifier.isStatic;
import static life.catalogue.es.mapping.ESDataType.NESTED;
import static life.catalogue.es.mapping.MappingUtil.createSimpleField;
import static life.catalogue.es.mapping.MappingUtil.isA;

/**
 * Generates an Elasticsearch document type mapping from a {@link Class} object.
 */
public class FieldMappingsFactory extends MappingsFactory {

  protected void addFields(ComplexField document, Class<?> type, Set<Class<?>> ancestors) {
    Set<String> fields = new HashSet<>();
    for (Field field : getMappedFields(type)) {
      String fieldName = field.getName();
      fields.add(fieldName);
      ESField esField = createField(field, ancestors);
      esField.setName(fieldName);
      document.addField(fieldName, esField);
    }
  }

  private ESField createField(Field field, Set<Class<?>> ancestors) {
    ESDataType esType;
    MapToType annotation = field.getAnnotation(MapToType.class);
    if (annotation != null) {
      esType = annotation.value();
    } else {
      Class<?> mapToType = getMappedType(field.getType(), field.getGenericType());
      esType = DataTypeMap.INSTANCE.getESType(mapToType);
      if (esType == null) { // we are dealing with a complex type
        if (ancestors.contains(mapToType)) {
          throw new ClassCircularityException(field, mapToType);
        }
        return createSubDocument(field, mapToType, MappingUtil.newAncestor(ancestors, mapToType));
      }
    }
    return createSimpleField(field, esType);
  }

  private ComplexField createSubDocument(Field field, Class<?> mapToType, Set<Class<?>> ancestors) {
    Class<?> realType = field.getType();
    ComplexField document;
    if (realType.isArray() || isA(realType, Collection.class)) {
      document = new ComplexField(NESTED);
    } else {
      document = new ComplexField();
    }
    addFields(document, mapToType, ancestors);
    return document;
  }

  private static ArrayList<Field> getMappedFields(Class<?> cls) {
    Set<String> names = new HashSet<>();
    ArrayList<Field> allFields = new ArrayList<>();
    while (cls != Object.class) {
      Field[] fields = cls.getDeclaredFields();
      for (Field f : fields) {
        if (!names.contains(f.getName())
            && !isStatic(f.getModifiers())
            && f.getAnnotation(NotMapped.class) == null
            && f.getAnnotation(JsonIgnore.class) == null) {
          names.add(f.getName());
          allFields.add(f);
        }
      }
      cls = cls.getSuperclass();
    }
    return allFields;
  }

}
