package org.col.db;

/**
 * A key/id lookup did not return any result, stopping the execution of the requested method
 * unexpectedly.
 */
public class NotInDatasetException extends NotFoundException {
  
  public NotInDatasetException(Class<?> entity, int datasetKey, String id) {
    super(entity, "datasetKey", datasetKey, "id", id);
  }

}
