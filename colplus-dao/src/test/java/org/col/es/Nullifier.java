package org.col.es;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets empty strings, arrays and collections within an object to null. This could be handy for SerDe tests for classes with List or Set
 * fields that are initialized to empty lists/sets. The Jackson ObjectMapper might nullify these fields upon serialization. In its current
 * state though too much of a wrecking ball to be really helpful.
 */
@SuppressWarnings("rawtypes")
public class Nullifier {

  private static final Logger LOG = LoggerFactory.getLogger(Nullifier.class);

  private final Class<?>[] nullifiables;

  /**
   * 
   * @param nullifiables The classes that the nullify method will recursively descend into
   */
  public Nullifier(Class<?>... nullifiables) {
    this.nullifiables = nullifiables;
  }

  public void nullify(Object... objects) {
    for (Object obj : objects) {
      nullifyOne(obj);
    }
  }

  public void nullify(Collection<?> objects) {
    objects.stream().forEach(this::nullifyOne);
  }

  private void nullifyOne(Object obj) {
    ArrayList<Field> fields = getFields(obj.getClass());
    try {
      MAIN_LOOP: for (Field f : fields) {
        Object val = f.get(obj);
        if (val == null) {
          continue;
        }
        if (f.getType() == String.class) {
          if (((String) val).isEmpty()) {
            nullifyInfo(f);
            f.set(obj, null);
          }
        } else if (isA(f.getType(), Collection.class)) {
          Collection col = (Collection) val;
          if (col.isEmpty()) {
            nullifyInfo(f);
            f.set(obj, null);
          } else {
            col.stream().forEach(this::nullifyElement);
          }
        } else if (f.getType().isArray()) {
          if (Array.getLength(val) == 0) {
            nullifyInfo(f);
            f.set(obj, null);
          } else {
            Arrays.stream((Object[]) val).forEach(this::nullifyElement);
          }
        } else {
          for (Class c : nullifiables) {
            if (isA(f.getType(), c)) {
              nullify(val);
              continue MAIN_LOOP;
            }
          }
        }
      }
    } catch (IllegalArgumentException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void nullifyElement(Object elem) {
    if (elem != null) {
      for (Class c : nullifiables) {
        if (isA(elem.getClass(), c)) {
          nullify(elem);
        }
      }
    }
  }

  private static ArrayList<Field> getFields(Class<?> cls) {
    ArrayList<Field> allFields = new ArrayList<>();
    while (cls != Object.class) {
      Field[] fields = cls.getDeclaredFields();
      for (Field f : fields) {
        if (!f.isAccessible()) {
          f.setAccessible(true);
        }
        allFields.add(f);
      }
      cls = cls.getSuperclass();
    }
    return allFields;
  }

  private static void nullifyInfo(Field f) {
    LOG.info("Nullifying field {}.{}", f.getDeclaringClass(), f.getName());
  }

  private static boolean isA(Class<?> cls, Class<?> superOrInterface) {
    return superOrInterface.isAssignableFrom(cls);
  }

}
