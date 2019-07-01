package org.col.api.search;

import java.util.Objects;

public class NameSuggestRequest {

  private String q;
  private Integer datasetKey;

  public String getQ() {
    return q;
  }

  public void setQ(String q) {
    this.q = q;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() == obj.getClass()) {
      return false;
    }
    NameSuggestRequest other = (NameSuggestRequest) obj;
    return Objects.equals(q, other.q)
        && Objects.equals(datasetKey, other.datasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, datasetKey);
  }
}
