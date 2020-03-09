package life.catalogue.api.search;

import java.util.Arrays;
import java.util.Objects;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class NameUsageRequest {

  @QueryParam("q")
  protected String q;
  @QueryParam("fuzzy")
  protected Boolean fuzzyMatching = Boolean.TRUE;
  protected String[] searchTerms;

  @JsonIgnore
  public boolean isEmpty() {
    return q == null && fuzzyMatching == null;
  }

  public boolean hasQ() {
    return StringUtils.isNotBlank(q);
  }

  public String getQ() {
    return q;
  }

  public Boolean getFuzzyMatching() {
    return fuzzyMatching;
  }

  public void setQ(String q) {
    this.q = q;
  }

  public void setFuzzyMatching(Boolean fuzzyMatching) {
    this.fuzzyMatching = fuzzyMatching;
  }

  @JsonIgnore
  public String[] getSearchTerms() {
    return searchTerms;
  }

  public void setSearchTerms(String[] searchTerms) {
    this.searchTerms = searchTerms;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(searchTerms);
    result = prime * result + Objects.hash(fuzzyMatching, q);
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
    return Objects.equals(fuzzyMatching, other.fuzzyMatching) && Objects.equals(q, other.q)
        && Arrays.equals(searchTerms, other.searchTerms);
  }

}
