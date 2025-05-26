package life.catalogue.api.event;

/**
 * Message to inform subscribers about a dataset that has changed data, e.g. to flush caches.
 */
public class DatasetDataChanged implements Event {
  public int datasetKey;

  public DatasetDataChanged() {
  }

  public DatasetDataChanged(int datasetKey) {
    this.datasetKey = datasetKey;
  }

}
