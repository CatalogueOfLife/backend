package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class NameUsageSuggestRequest extends NameUsageRequest {

  @QueryParam("datasetKey")
  private Integer datasetKey;
  @QueryParam("vernaculars")
  private boolean vernaculars;
  @QueryParam("accepted")
  private boolean accepted;
  @QueryParam("limit")
  private Integer limit; // Desired number of suggestions

  @Override
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public SearchType getSearchType() {
    return SearchType.PREFIX;
  }

  public boolean isVernaculars() {
    return vernaculars;
  }

  public void setVernaculars(boolean vernaculars) {
    this.vernaculars = vernaculars;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public void setAccepted(boolean accepted) {
    this.accepted = accepted;
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

  public Boolean getAccepted() {
    return accepted;
  }

  public void setAccepted(Boolean accepted) {
    this.accepted = accepted;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty() && datasetKey == null && !vernaculars && !accepted && limit == null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(accepted, datasetKey, limit, vernaculars);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    NameUsageSuggestRequest other = (NameUsageSuggestRequest) obj;
    return Objects.equals(accepted, other.accepted) 
        && Objects.equals(datasetKey, other.datasetKey) 
        && Objects.equals(limit, other.limit)
        && Objects.equals(vernaculars, other.vernaculars);
  }

}
