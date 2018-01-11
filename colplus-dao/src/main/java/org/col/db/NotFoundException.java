package org.col.db;

import java.util.LinkedHashMap;
import java.util.Map;

public class NotFoundException extends RuntimeException {

  private static String createMessage(Class<?> entity, Object... kvPairs) {
    StringBuilder sb = new StringBuilder(50);
    sb.append("No such ").append(entity.getSimpleName()).append(": ");
    for (int i = 0; i < kvPairs.length; i++) {
      if (i != 0) {
        sb.append(";");
      }
      sb.append(kvPairs[i]).append("=").append(kvPairs[i + 1]);
    }
    return sb.toString();
  }

  private static Map<String, Object> createKey(Object... kvPairs) {
    Map<String, Object> key = new LinkedHashMap<>();
    for (int i = 0; i < kvPairs.length; i++) {
      key.put(kvPairs[i].toString(), kvPairs[i + i]);
    }
    return key;
  }

  private Class<?> entity;
  private Map<String, Object> key;

  public NotFoundException(Class<?> entity, String key, Object value) {
    super(createMessage(entity, key, value));
    this.entity = entity;
    this.key = createKey(key, value);
  }

  public NotFoundException(Class<?> entity, String key0, Object value0, String key1,
      Object value1) {
    super(createMessage(entity, key0, value0, key1, value1));
    this.entity = entity;
    this.key = createKey(key0, value0, key1, value1);
  }

  public NotFoundException(Class<?> entity, String key0, Object value0, String key1,
      Object value1, String key2, Object value2) {
    super(createMessage(entity, key0, value0, key1, value1, key2, value2));
    this.entity = entity;
    this.key = createKey(key0, value0, key1, value1, key2, value2);
  }

  public NotFoundException(Class<?> entity, String key0, Object value0, String key1,
      Object value1, String key2, Object value2, String key3, Object value3) {
    super(createMessage(entity, key0, value0, key1, value1, key2, value2, key3, value3));
    this.entity = entity;
    this.key = createKey(key0, value0, key1, value1, key2, value2, key3, value3);
  }

  public Class<?> getEntity() {
    return entity;
  }

  public Map<String, Object> getKey() {
    return key;
  }

}
