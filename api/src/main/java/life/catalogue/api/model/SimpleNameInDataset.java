package life.catalogue.api.model;

import java.util.Objects;

public class SimpleNameInDataset extends SimpleName {
  private int datasetKey;

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SimpleNameInDataset that = (SimpleNameInDataset) o;
    return datasetKey == that.datasetKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetKey);
  }
}
