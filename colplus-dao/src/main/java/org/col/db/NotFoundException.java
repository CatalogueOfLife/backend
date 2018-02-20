package org.col.db;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class NotFoundException extends RuntimeException {

  private final static Joiner.MapJoiner PARAM_JOINER = Joiner.on(", ")
      .withKeyValueSeparator("=")
      .useForNull("null");

  private static String createMessage(Class<?> entity, Map<String, Object> params) {
    return "No such " + entity.getSimpleName() + ": " + PARAM_JOINER.join(params);
  }

  private static Map<String, Object> createKey(Object... kvPairs) {
    Map<String, Object> key = new LinkedHashMap<>();
    for (int i = 0; i < kvPairs.length; i += 2) {
      key.put(kvPairs[i].toString(), kvPairs[i + 1]);
    }
    return key;
  }

  private final Class<?> entity;
  private final Map<String, Object> params;

  public NotFoundException(Class<?> entity, Map<String, Object> params) {
    super(createMessage(entity, params));
    this.entity = entity;
    this.params = params;
  }

  public NotFoundException(Class<?> entity, String key, Object value) {
    this(entity, ImmutableMap.of(key, value));
  }

  public NotFoundException(Class<?> entity, String key0, Object value0, String key1, Object value1) {
    this(entity, ImmutableMap.of(key0, value0, key1, value1));
  }

  public Class<?> getEntity() {
    return entity;
  }

  public Map<String, Object> getParams() {
    return params;
  }

}
