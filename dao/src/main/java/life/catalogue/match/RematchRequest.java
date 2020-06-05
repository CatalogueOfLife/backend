package life.catalogue.match;

import java.util.Objects;

public class RematchRequest {
  private Integer id;
  private int datasetKey; // project
  private boolean brokenOnly;

  public RematchRequest() {
  }

  public RematchRequest(Integer id) {
    this.id = id;
  }

  public RematchRequest(int datasetKey, boolean brokenOnly) {
    this.datasetKey = datasetKey;
    this.brokenOnly = brokenOnly;
  }


  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public boolean isBrokenOnly() {
    return brokenOnly;
  }

  public void setBrokenOnly(boolean brokenOnly) {
    this.brokenOnly = brokenOnly;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RematchRequest)) return false;
    RematchRequest that = (RematchRequest) o;
    return datasetKey == that.datasetKey &&
      brokenOnly == that.brokenOnly &&
      Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, brokenOnly);
  }
}
