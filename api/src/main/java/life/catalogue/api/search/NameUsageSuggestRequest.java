package life.catalogue.api.search;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.ws.rs.QueryParam;

public class NameUsageSuggestRequest extends NameUsageRequest {

  @QueryParam("accepted")
  private boolean accepted;
  @QueryParam("exclBareNames")
  private boolean exclBareNames;
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

  public boolean isExclBareNames() {
    return exclBareNames;
  }

  public void setExclBareNames(boolean exclBareNames) {
    this.exclBareNames = exclBareNames;
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
  public boolean equals(Object o) {
    if (!(o instanceof NameUsageSuggestRequest)) return false;
    if (!super.equals(o)) return false;
    NameUsageSuggestRequest that = (NameUsageSuggestRequest) o;
    return accepted == that.accepted && exclBareNames == that.exclBareNames && Objects.equals(limit, that.limit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accepted, exclBareNames, limit);
  }
}
