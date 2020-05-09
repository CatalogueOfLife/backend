package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base class for {@link NameUsageSearchRequest} and {@link NameUsageSuggestRequest}.
 * 
 */
public abstract class NameUsageRequest {

  @QueryParam("q")
  protected String q;
  @QueryParam("fuzzy")
  protected boolean fuzzy = false;
  protected String[] searchTerms;

  /**
   * Whether or not to apply fuzzing matching for scientific names. Will always return true for the suggestion service (whole word matching
   * defies the purpose of an auto-completion), but is user-configurable for the search service.
   */
  public abstract boolean isPrefix();

  @JsonIgnore
  public boolean isEmpty() {
    return q == null && !fuzzy;
  }

  @JsonIgnore // These are derived & set after the request comes in
  public String[] getSearchTerms() {
    return searchTerms;
  }

  public void setSearchTerms(String[] searchTerms) {
    this.searchTerms = searchTerms;
  }

  public boolean hasQ() {
    return StringUtils.isNotBlank(q);
  }

  public String getQ() {
    return q;
  }

  public void setQ(String q) {
    this.q = q;
  }

  public boolean isFuzzy() {
    return fuzzy;
  }

  public void setFuzzy(boolean fuzzy) {
    this.fuzzy = fuzzy;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(searchTerms);
    result = prime * result + Objects.hash(fuzzy, q);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    NameUsageRequest other = (NameUsageRequest) obj;
    return fuzzy == other.fuzzy && Objects.equals(q, other.q)
        && Arrays.equals(searchTerms, other.searchTerms);
  }

}
