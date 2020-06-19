package life.catalogue.api.exception;

import life.catalogue.api.model.DSID;

public class NotFoundException extends RuntimeException {
  private final Object key;

  public Object getKey() {
    return key;
  }

  public NotFoundException(String message) {
    super(message);
    this.key = null;
  }

  public NotFoundException(Object key, String message) {
    super(message);
    this.key = key;
  }

  public NotFoundException(Object key, String message, Throwable cause) {
    super(message, cause);
    this.key = key;
  }

  public static NotFoundException notFound(String entityName, Object key) {
    return new NotFoundException(key, createMessage(entityName, key.toString()));
  }

  public static NotFoundException notFound(Class<?> entity, Object key) {
    return new NotFoundException(key, createMessage(entity, key.toString()));
  }

  public static NotFoundException notFound(Class<?> entity, DSID<?> key) {
    return new NotFoundException(key, createMessage(entity, key.concat()));
  }

  public static NotFoundException notFound(Class<?> entity, int datasetKey, String id) {
    return notFound(entity, DSID.of(datasetKey, id));
  }

  private static String createMessage(Class<?> entity, String key) {
    return createMessage(entity.getSimpleName(), key);
  }

  private static String createMessage(String entityName, String key) {
    return entityName + " " + key + " does not exist";
  }
}
