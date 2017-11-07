package org.col.db;

/**
 * A key/id lookup did not return any result, stopping the execution of the requested method unexpectedly.
 */
public class NotFoundException extends RuntimeException {
  private final int datasetKey;
  private final String id;

  public NotFoundException(int datasetKey, String id) {
    this("entity", datasetKey, id);
  }

  public NotFoundException(Class entity, int datasetKey, String id) {
    this(entity.getSimpleName(), datasetKey, id);
  }

  private NotFoundException(String entity, int datasetKey, String id) {
    super("The "+entity+" "+datasetKey+":"+id+" could not be found");
    this.datasetKey = datasetKey;
    this.id = id;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public String getId() {
    return id;
  }
}
