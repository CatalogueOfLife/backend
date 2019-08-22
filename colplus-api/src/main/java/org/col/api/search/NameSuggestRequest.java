package org.col.api.search;

import java.util.Objects;

public class NameSuggestRequest {

  private String q;
  private Integer datasetKey;
  private Integer limit; // desired number of suggested names
  private Boolean vernaculars; // include vernacular names among the suggestions?
  private Boolean simple; // only match search phrase against lowercased scientific name?

  public boolean isSimple() {
    return simple != null && simple.equals(Boolean.TRUE);
  }

  public boolean isVernaculars() {
    return vernaculars != null && vernaculars.equals(Boolean.TRUE);
  }

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

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Boolean getSimple() {
    return simple;
  }

  public void setSimple(Boolean simple) {
    this.simple = simple;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, limit, q, simple, vernaculars);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameSuggestRequest other = (NameSuggestRequest) obj;
    return Objects.equals(datasetKey, other.datasetKey) && Objects.equals(limit, other.limit) && Objects.equals(q, other.q)
        && Objects.equals(simple, other.simple) && Objects.equals(vernaculars, other.vernaculars);
  }

}
