package life.catalogue.api.search;

import java.util.Objects;
import javax.ws.rs.QueryParam;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class NameUsageSuggestRequest extends NameUsageRequest {

  @QueryParam("datasetKey")
  private Integer datasetKey;
  @QueryParam("vernaculars")
  private Boolean vernaculars;
  @QueryParam("limit")
  private Integer limit; // Desired number of suggestions

  @Override
  public boolean isPrefix() {
    return true; // false defies the purpose of auto-complete
  }

  @JsonIgnore
  public boolean suggestVernaculars() {
    return vernaculars != null && vernaculars.equals(Boolean.TRUE);
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

  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty() && datasetKey == null && vernaculars == null && limit == null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(datasetKey, limit, vernaculars);
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
    return Objects.equals(datasetKey, other.datasetKey) && Objects.equals(limit, other.limit)
        && Objects.equals(vernaculars, other.vernaculars);
  }

}
