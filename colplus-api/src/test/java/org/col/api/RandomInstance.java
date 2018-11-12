package org.col.api;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.apache.commons.lang3.StringUtils;

public class RandomInstance {
  
  private static final Random RND = new Random();
  
  // Upper bounds for randomized values
  private int maxInt = 100;
  private int maxStringLength = 10;
  private int maxArrayLength = 5;
  
  /*
   * Whether or not to set "empty" strings/arrays/collections to null. MUST be true in combination
   * with JsonInclude.Include.NON_EMPTY !! Otherwise SerDe tests will arbitrarily fail or succeed.
   */
  private boolean emptyToNull = true;
  
  /*
   * Initializes commonly-typed fields (String, int, ...) of an instance of type T.
   */
  @SuppressWarnings({"unchecked"})
  public <T> void populate(T instance) {
    Class<T> c = (Class<T>) instance.getClass();
    try {
      for (Field f : getFields(c)) {
        setCommonTypes(instance, f);
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
  
  /*
   * Instantiate and initialize commonly-typed fields. Only works if c has a no-arg constructor.
   * Fields whose type is in the extraTypes array will also be initialized (again requires a no-arg
   * constructor for that type).
   */
  public Object create(Class<?> c, Class<?>... extraTypes) {
    Set<Class<?>> set = new HashSet<>(Arrays.asList(extraTypes));
    try {
      Object instance = c.newInstance();
      for (Field f : getFields(c)) {
        if (setCommonTypes(instance, f)) {
          continue;
        }
        Class<?> t = f.getType();
        if (set.contains(t)) {
          f.set(instance, create(t, extraTypes));
        } else if (t.isArray() && set.contains(t.getComponentType())) {
          f.set(instance, createArray(t.getComponentType(), extraTypes));
        }
      }
      return instance;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
  
  public List<?> createList(Class<?> c, Class<?>... extraTypes) {
    int size = RND.nextInt(maxArrayLength + 1);
    if (size == 0) {
      return emptyToNull ? null : Collections.emptyList();
    }
    List<Object> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(create(c, extraTypes));
    }
    return list;
  }
  
  private Object[] createArray(Class<?> c, Class<?>... extraTypes) {
    int size = RND.nextInt(maxArrayLength + 1);
    Object[] arr = (Object[]) Array.newInstance(c, size);
    if (size == 0) {
      return emptyToNull ? null : arr;
    }
    for (int i = 0; i < size; i++) {
      arr[i] = create(c, extraTypes);
    }
    return arr;
  }
  
  public void setMaxInt(int maxInt) {
    this.maxInt = maxInt;
  }
  
  public void setMaxStringLength(int maxStringLength) {
    this.maxStringLength = maxStringLength;
  }
  
  public void setMaxArrayLength(int maxArrayLength) {
    this.maxArrayLength = maxArrayLength;
  }
  
  public void setEmptyToNull(boolean emptyToNull) {
    this.emptyToNull = emptyToNull;
  }
  
  private <T> boolean setCommonTypes(T instance, Field f) throws IllegalAccessException {
    Class<?> t = f.getType();
    if (t == String.class) {
      f.set(instance, randomString());
      return true;
    } else if (t == String[].class) {
      String[] strings = new String[RND.nextInt(maxArrayLength + 1)];
      for (int i = 0; i < strings.length; i++) {
        strings[i] = randomString();
        f.set(instance, strings);
      }
      return true;
    } else if (t == int.class) {
      f.setInt(instance, RND.nextInt(maxInt + 1));
      return true;
    } else if (t == Integer.class) {
      f.set(instance, RND.nextInt(maxInt + 1));
      return true;
    } else if (t == boolean.class) {
      f.setBoolean(instance, randomBoolean());
      return true;
    } else if (t == Boolean.class) {
      f.set(instance, randomBoolean());
      return true;
    } else if (t.isEnum()) {
      Class<Enum<?>> enumClass = (Class<Enum<?>>) t;
      Enum<?>[] values = enumClass.getEnumConstants();
      int idx = RND.nextInt(values.length);
      f.set(instance, values[idx]);
      return true;
    } else if (t == LocalDateTime.class) {
      f.set(instance, randomDateTime());
      return true;
    } else if (t == LocalDate.class) {
      f.set(instance, randomDateTime().toLocalDate());
      return true;
    }
    // TODO more common types
    return false;
  }
  
  private LocalDateTime randomDateTime() {
    return LocalDateTime.now();
  }
  
  private String randomString() {
    int len = RND.nextInt(maxStringLength + 1);
    if (len == 0) {
      return emptyToNull || randomBoolean() ? null : StringUtils.EMPTY;
    }
    return RandomUtils.randomString(len);
  }
  
  private static boolean randomBoolean() {
    return RND.nextInt(2) == 1;
  }
  
  private static ArrayList<Field> getFields(Class<?> cls) {
    ArrayList<Class<?>> hierarchy = new ArrayList<>(4);
    Class<?> c = cls;
    while (c != Object.class) {
      hierarchy.add(c);
      c = c.getSuperclass();
    }
    ArrayList<Field> allFields = new ArrayList<>();
    for (int i = hierarchy.size() - 1; i >= 0; i--) {
      c = hierarchy.get(i);
      Field[] fields = c.getDeclaredFields();
      for (Field f : fields) {
        if (Modifier.isStatic(f.getModifiers())) {
          continue;
        }
        if (!f.isAccessible()) {
          f.setAccessible(true);
        }
        allFields.add(f);
      }
    }
    return allFields;
  }
  
}
