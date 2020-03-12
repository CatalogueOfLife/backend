package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.Objects;

public abstract class NameUsageRequest {

  @QueryParam("q")
  protected String q;
  @QueryParam("fuzzy")
  protected boolean fuzzyMatchingEnabled = true;
  protected String[] searchTerms;

  public abstract boolean isPrefixMatching();

  @JsonIgnore
  public boolean isEmpty() {
    return q == null && !fuzzyMatchingEnabled;
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

  public boolean isFuzzyMatchingEnabled() {
    return fuzzyMatchingEnabled;
  }

  public void setFuzzyMatchingEnabled(boolean fuzzyMatchingEnabled) {
    this.fuzzyMatchingEnabled = fuzzyMatchingEnabled;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(searchTerms);
    result = prime * result + Objects.hash(fuzzyMatchingEnabled, q);
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
    return fuzzyMatchingEnabled == other.fuzzyMatchingEnabled && Objects.equals(q, other.q)
        && Arrays.equals(searchTerms, other.searchTerms);
  }

}
