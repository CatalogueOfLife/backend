package life.catalogue.api.event;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetDataChanged)) return false;
    DatasetDataChanged that = (DatasetDataChanged) o;
    return datasetKey == that.datasetKey;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(datasetKey);
  }
}
