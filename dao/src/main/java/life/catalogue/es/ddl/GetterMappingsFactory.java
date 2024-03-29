package life.catalogue.es.ddl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static life.catalogue.es.ddl.ESDataType.NESTED;
import static life.catalogue.es.ddl.MappingUtil.*;

/**
 * Generates an Elasticsearch document type mapping from the getters of a {@link Class}.
 */
class GetterMappingsFactory extends MappingsFactory {

  protected void addFields(ComplexField document, Class<?> type, Set<Class<?>> ancestors) {
    Set<String> fields = new HashSet<>();
    for (Method method : getMappedProperties(type)) {
      String fieldName = getFieldName(method);
      fields.add(fieldName);
      ESField esField = createField(method, ancestors);
      esField.setName(fieldName);
      document.addField(fieldName, esField);
    }
  }

  private ESField createField(Method method, Set<Class<?>> ancestors) {
    ESDataType esType;
    MapToType annotation = method.getAnnotation(MapToType.class);
    if (annotation != null) {
      if (annotation.value() == ESDataType.OBJECT || annotation.value() == ESDataType.NESTED) {
        if (!isA(method.getReturnType(), Object[].class) && !isA(method.getReturnType(), Collection.class)) {
          throw new MappingException(
              String.format("Illegal mapping from java type %s to ES datatype %s", method.getReturnType(), annotation.value()));
        }
        return createSubDocument(method, getMappedType(method), ancestors);
      }
      esType = annotation.value();
    } else {
      Class<?> mapToType = getMappedType(method);
      esType = DataTypeMap.INSTANCE.getESType(mapToType);
      if (esType == null) { // we are dealing with a complex type
        if (ancestors.contains(mapToType)) {
          throw new ClassCircularityException(method, mapToType);
        }
        return createSubDocument(method, mapToType, newAncestor(ancestors, mapToType));
      }
    }
    return createSimpleField(method, esType);
  }

  private ComplexField createSubDocument(Method method, Class<?> mapToType, Set<Class<?>> ancestors) {
    Class<?> realType = method.getReturnType();
    ComplexField document;
    if (isA(realType, Object[].class) || isA(realType, Collection.class)) {
      /*
       * Ordinarily arrays or collections of objects should **always** be mapped to the NESTED datatype.
       * Otherwise searching on a combination of fields within the object will give unexpected (wrong)
       * results. However, if none (or just one) of the fields within the object are indexed , this is not
       * relevant any longer, because you can't search on them anyway. So mapping it to type OBJECT will
       * save you the creation of separate subdocuments.
       */
      MapToType annotation = method.getAnnotation(MapToType.class);
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

  private static ArrayList<Method> getMappedProperties(Class<?> cls) {
    Set<String> names = new HashSet<>();
    ArrayList<Method> allMethods = new ArrayList<>();
    while (cls != Object.class) {
      Method[] methods = cls.getDeclaredMethods();
      for (Method m : methods) {
        if (!names.contains(m.getName())
            && !isStatic(m.getModifiers())
            && isPublic(m.getModifiers())
            && isGetter(m)
            && m.getAnnotation(NotMapped.class) == null
            && m.getAnnotation(JsonIgnore.class) == null) {
          names.add(m.getName());
          allMethods.add(m);
        }
      }
      cls = cls.getSuperclass();
    }
    return allMethods;
  }

  private static String getFieldName(Method getter) {
    String name = getter.getName();
    if (name.startsWith("get") && name.length() > 3 && isUpperCase(name.charAt(3))) {
      return toLowerCase(name.charAt(3)) + name.substring(4);
    }
    if (name.startsWith("is") && name.length() > 2 && isUpperCase(name.charAt(2))) {
      return toLowerCase(name.charAt(2)) + name.substring(3);
    }
    return null;
  }

  private static boolean isGetter(Method m) {
    if (m.getReturnType() == void.class) {
      return false;
    }
    if (m.getParameterCount() != 0) {
      return false;
    }
    return getFieldName(m) != null;
  }

}
