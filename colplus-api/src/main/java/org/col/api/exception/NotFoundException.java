package org.col.api.exception;

import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

public class NotFoundException extends RuntimeException {
  
  private final static Joiner.MapJoiner PARAM_JOINER = Joiner.on(", ")
      .withKeyValueSeparator("=")
      .useForNull("null");
  
  private static String createMessage(Class<?> entity, Map<String, Object> params) {
    return createMessage(entity, PARAM_JOINER.join(params));
  }
  
  private static String createMessage(Class<?> entity, String param) {
    return "No such " + entity.getSimpleName() + ": " + param;
  }

  public NotFoundException(String message) {
    super(message);
  }
  
  public static NotFoundException keyNotFound(Class<?> entity, Object key) {
    return new NotFoundException(entity, ImmutableMap.of("key", key));
  }
  
  public static NotFoundException idNotFound(Class<?> entity, int datasetKey, String id) {
    return new NotFoundException(entity, ImmutableMap.of("datasetKey", datasetKey, "id", id));
  }
  
  public NotFoundException(Class<?> entity, Map<String, Object> params) {
    super(createMessage(entity, params));
  }
  
  public NotFoundException(Class<?> entity, String name) {
    super(createMessage(entity, name));
  }
}
