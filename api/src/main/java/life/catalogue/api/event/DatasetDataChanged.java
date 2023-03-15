package life.catalogue.api.event;

/**
 * Message to inform subscribers about a dataset that has changed data, e.g. to flush caches.
 */
public class DatasetDataChanged {
  public final int datasetKey;

  public DatasetDataChanged(int datasetKey) {
    this.datasetKey = datasetKey;
  }

}
