package life.catalogue.api.search;

import java.util.Arrays;
import java.util.Objects;
import javax.ws.rs.QueryParam;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Base class for {@link NameUsageSearchRequest} and {@link NameUsageSuggestRequest}.
 * 
 */
public abstract class NameUsageRequest {

  /**
   * Symbolic constants for the available search types within the Catalogue of Life.
   */
  public static enum SearchType {
    /**
     * Matches a search term to the beginning of the epithets within a scientific name. This is the only
     * available search type for the suggest service. Whole-word and exact matching defies the purpose
     * of auto-completion. However, you still have the option of fuzzy/non-fuzzy matching.
     */
    PREFIX,
    /**
     * Matches a search term to entire epithets within a scientific name.
     */
    WHOLE_WORDS,
    /**
     * Matches the entire search phrase to the entire scientific name. When choosing this type you
     * cannot also opt for fuzzy matching. The "fuzzy" parameter is silently ignored.
     */
    EXACT
  }

  @QueryParam("q")
  protected String q;
  @QueryParam("fuzzy")
  protected boolean fuzzy = false;
  protected String[] searchTerms;

  public abstract SearchType getSearchType();

  @JsonIgnore
  public boolean isEmpty() {
    return q == null && !fuzzy;
  }

  /**
   * The search terms analyzed as appropriate for scientific name searches. Should not be used for
   * vernacular name and authorship searches. The search terms are derived from the Q and are set
   * programmatically.
   * 
   * @return
   */
  @JsonIgnore
  public String[] getSciNameSearchTerms() {
    return searchTerms;
  }

  public void setSciNameSearchTerms(String[] searchTerms) {
    this.searchTerms = searchTerms;
  }

  public boolean hasQ() {
    return StringUtils.isNotBlank(q);
  }

  public String getQ() {
    return q;
  }

  public void setQ(String q) {
    /*
     * BEWARE: currently ALL analyzers involved in q matching (whether on scientific name, vernacular
     * name or authorship) churn out all-lowercase tokens. Therefore, we might as well be done with it
     * and lowercase the Q as well as soon as possible (i.e. here). Otherwise it's easy to forget (issue
     * #697). If case-sensitive matching is introduced for some reason, we must change this setter into
     * a regular setter and be **very** careful to check **all** classes that implicitly assume the Q to
     * be lowercase (the QMatcher classes, the Highlighter classes, etc.) In other words check all
     * references to NameUsageRequest.getQ()
     */
    if (q != null) {
      this.q = q.toLowerCase();
    }
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
