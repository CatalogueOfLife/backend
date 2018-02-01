package org.col.dw.db;

public class KeyNotFoundException extends NotFoundException {

  public KeyNotFoundException(Class<?> entity, Integer key) {
    super(entity, "key", key);
  }

}
