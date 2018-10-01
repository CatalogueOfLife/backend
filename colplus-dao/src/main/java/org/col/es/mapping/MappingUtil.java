package org.col.es.mapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

class MappingUtil {

  /*
   * Returns the type argument for a generic type (e.g. Person for List<Person>)
   */
  static Class<?> getClassForTypeArgument(Type t) {
    String s = t.toString();
    int i = s.indexOf('<');
    s = s.substring(i + 1, s.length() - 1);
    try {
      return Class.forName(s);
    } catch (ClassNotFoundException e) {
      throw new MappingException(e);
    }
  }

  static ArrayList<Field> getFields(Class<?> cls) {
    Set<String> names = new HashSet<>();
    ArrayList<Field> allFields = new ArrayList<>();
    while (cls != Object.class) {
      Field[] fields = cls.getDeclaredFields();
      for (Field f : fields) {
        if (Modifier.isStatic(f.getModifiers()))
          continue;
        if (f.getAnnotation(JsonIgnore.class) != null)
          continue;
        if (names.contains(f.getName()))
          continue;
        if (!Modifier.isPublic(f.getModifiers()))
          continue;
        names.add(f.getName());
        allFields.add(f);
      }
      cls = cls.getSuperclass();
    }
    return allFields;
  }

  static ArrayList<Method> getMappedProperties(Class<?> cls) {
    Set<String> names = new HashSet<>();
    ArrayList<Method> allMethods = new ArrayList<>();
    while (cls != Object.class) {
      Method[] methods = cls.getDeclaredMethods();
      for (Method m : methods) {
        if (Modifier.isStatic(m.getModifiers()))
          continue;
        if (m.getAnnotation(JsonIgnore.class) != null)
          continue;
        if (names.contains(m.getName()))
          continue;
        if (!Modifier.isPublic(m.getModifiers()))
          continue;
        if (m.getReturnType() == Void.class)
          continue;
        if (m.getParameterCount() != 0)
          continue;
        if (!isGetter(m))
          continue;

        names.add(m.getName());
        allMethods.add(m);
      }
      cls = cls.getSuperclass();
    }
    return allMethods;
  }

  private static boolean isGetter(Method m) {
    if (m.getReturnType() == Void.class)
      return false;
    if (m.getParameterCount() != 0)
      return false;
    return extractProperty(m.getName()) != null;
  }

  static String extractProperty(String getter) {
    if (getter.startsWith("get") && getter.length() > 3 && isUpperCase(getter.charAt(3))) {
      return toLowerCase(getter.charAt(3)) + getter.substring(4);
    }
    if (getter.startsWith("is") && getter.length() > 2 && isUpperCase(getter.charAt(2))) {
      return toLowerCase(getter.charAt(2)) + getter.substring(3);
    }
    return null;
  }

}
