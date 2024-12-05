package life.catalogue.api.search;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.ws.rs.QueryParam;

public class NameUsageSuggestRequest extends NameUsageRequest {

  @QueryParam("accepted")
  private boolean accepted;
  @QueryParam("limit")
  private Integer limit; // Desired number of suggestions

  @Override
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public SearchType getSearchType() {
    return SearchType.PREFIX;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public void setAccepted(boolean accepted) {
    this.accepted = accepted;
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
    return super.isEmpty() && !accepted && limit == null;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(accepted, limit);
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
        && Objects.equals(limit, other.limit);
  }

}
