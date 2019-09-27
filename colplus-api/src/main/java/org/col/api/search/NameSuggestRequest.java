package org.col.api.search;

import java.util.Objects;

import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class NameSuggestRequest {

  @QueryParam("q")
  private String q;
  @QueryParam("datasetKey")
  private Integer datasetKey;
  // Desired number of suggestions
  @QueryParam("limit")
  private Integer limit;
  // Suggest vernacular names as well?
  @QueryParam("vernaculars")
  private Boolean suggestVernaculars;

  @JsonIgnore
  public boolean isSuggestVernaculars() {
    return suggestVernaculars != null && suggestVernaculars.equals(Boolean.TRUE);
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

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, limit, q, suggestVernaculars);
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
        && Objects.equals(suggestVernaculars, other.suggestVernaculars);
  }

}
