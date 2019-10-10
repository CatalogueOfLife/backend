package org.col.es.mapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

class MappingUtil {

  /*
   * Returns the type argument for a generic type (e.g. Person for List<Person>)
   */
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

  static ArrayList<Method> getMappedProperties(Class<?> cls) {
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

  static ArrayList<Field> getMappedFields(Class<?> cls) {
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

  static String getFieldName(Method getter) {
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
