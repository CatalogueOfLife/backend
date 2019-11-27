package life.catalogue.api.search;

import java.util.Objects;

import javax.ws.rs.QueryParam;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class NameUsageSuggestRequest {

  @QueryParam("q")
  private String q;
  @QueryParam("datasetKey")
  private Integer datasetKey;
  @QueryParam("vernaculars")
  private Boolean vernaculars;
  @QueryParam("limit")
  private Integer limit; // Desired number of suggestions

  @JsonIgnore
  public boolean suggestVernaculars() {
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

  public Boolean getVernaculars() {
    return vernaculars;
  }

  public void setVernaculars(Boolean vernaculars) {
    this.vernaculars = vernaculars;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, limit, q, vernaculars);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameUsageSuggestRequest other = (NameUsageSuggestRequest) obj;
    return Objects.equals(datasetKey, other.datasetKey) && Objects.equals(limit, other.limit) && Objects.equals(q, other.q)
        && Objects.equals(vernaculars, other.vernaculars);
  }

}
