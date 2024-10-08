package life.catalogue.es.ddl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static java.lang.reflect.Modifier.isStatic;
import static life.catalogue.es.ddl.ESDataType.NESTED;
import static life.catalogue.es.ddl.MappingUtil.createSimpleField;
import static life.catalogue.es.ddl.MappingUtil.isA;

/**
 * Generates an Elasticsearch document type mapping from the fields {@link Class}.
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
      if (annotation.value() == ESDataType.OBJECT || annotation.value() == ESDataType.NESTED) {
        if (!isA(field.getType(), Object[].class) && !isA(field.getType(), Collection.class)) {
          throw new MappingException(
              String.format("Illegal mapping from java type %s to ES datatype %s", field.getType(), annotation.value()));
        }
        return createSubDocument(field, getMappedType(field), ancestors);
      }
      esType = annotation.value();
    } else {
      Class<?> mapToType = getMappedType(field);
      esType = DataTypeMap.INSTANCE.getESType(mapToType);
      if (esType == null) { // we are dealing with a complex type (an object or nested document)
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
    if (isA(realType, Object[].class) || isA(realType, Collection.class)) {
      /*
       * Ordinarily arrays or collections of objects should **always** be mapped to the NESTED datatype.
       * Otherwise searching on a combination of fields within the object will give unexpected (wrong)
       * results. However, if none (or just one) of the fields within the object are indexed , this is not
       * relevant any longer, because you can't search on them anyway. So mapping it to type OBJECT will
       * save you the creation of separate subdocuments.
       */
      MapToType annotation = field.getAnnotation(MapToType.class);
      if (annotation != null && annotation.value() == ESDataType.OBJECT) {
        document = new ComplexField();
      } else {
        document = new ComplexField(NESTED);
      }
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
