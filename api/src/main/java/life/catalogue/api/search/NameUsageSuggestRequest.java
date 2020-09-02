package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class NameUsageSuggestRequest extends NameUsageRequest {

  @QueryParam("datasetKey")
  private Integer datasetKey;
  @QueryParam("vernaculars")
  private Boolean vernaculars;
  @QueryParam("accepted")
  private Boolean accepted;

  @QueryParam("limit")
  private Integer limit; // Desired number of suggestions

  @Override
  public SearchType getSearchType() {
    return SearchType.PREFIX;
  }

  @JsonIgnore
  public boolean isVernaculars() {
    return vernaculars != null && vernaculars;
  }

  @JsonIgnore
  public boolean isAccepted() {
    return accepted != null && accepted;
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
    return super.isEmpty() && datasetKey == null && vernaculars == null && accepted == null && limit == null;
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
