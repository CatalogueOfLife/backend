package life.catalogue.api.event;

import java.util.Objects;

/**
 * Message to inform subscribers about a change of the dataset logo image.
 */
public class DatasetLogoChanged implements Event {
  public int datasetKey;

  public DatasetLogoChanged() {
  }

  public DatasetLogoChanged(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetLogoChanged)) return false;
    DatasetLogoChanged that = (DatasetLogoChanged) o;
    return datasetKey == that.datasetKey;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(datasetKey);
  }
}
