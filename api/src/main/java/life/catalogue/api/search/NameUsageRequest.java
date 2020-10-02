package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.Objects;

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

  @QueryParam("minRank")
  private Rank minRank;

  @QueryParam("maxRank")
  private Rank maxRank;

  @QueryParam("sortBy")
  private NameUsageSearchRequest.SortBy sortBy;

  @QueryParam("reverse")
  private boolean reverse;

  protected String[] searchTerms;


  public NameUsageRequest() {
  }

  public NameUsageRequest(String q, boolean fuzzy, Rank minRank, Rank maxRank, NameUsageSearchRequest.SortBy sortBy, boolean reverse) {
    this.q = q;
    this.fuzzy = fuzzy;
    this.minRank = minRank;
    this.maxRank = maxRank;
    this.sortBy = sortBy;
    this.reverse = reverse;
  }

  public NameUsageRequest(NameUsageRequest other) {
    this.q = other.q;
    this.fuzzy = other.fuzzy;
    this.searchTerms = other.searchTerms;
    this.minRank = other.minRank;
    this.maxRank = other.maxRank;
    this.sortBy = other.sortBy;
    this.reverse = other.reverse;
  }

  public abstract SearchType getSearchType();

  @JsonIgnore
  public boolean isEmpty() {
    return q == null
      && !fuzzy
      && minRank==null
      && maxRank==null
      && sortBy == null
      && !reverse;
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
    this.q = q;
  }

  public boolean isFuzzy() {
    return fuzzy;
  }

  public void setFuzzy(boolean fuzzy) {
    this.fuzzy = fuzzy;
  }

  public Rank getMinRank() {
    return minRank;
  }

  public void setMinRank(Rank minRank) {
    this.minRank = minRank;
  }

  /**
   * Filters usages by their rank, excluding all usages with a higher rank then the one given. E.g.
   * maxRank=FAMILY will include usages of rank family and below (genus, species, etc), but exclude
   * all orders and above.
   */
  public Rank getMaxRank() {
    return maxRank;
  }

  public void setMaxRank(Rank maxRank) {
    this.maxRank = maxRank;
  }

  public NameUsageSearchRequest.SortBy getSortBy() {
    return sortBy;
  }

  public void setSortBy(NameUsageSearchRequest.SortBy sortBy) {
    this.sortBy = sortBy;
  }

  public boolean isReverse() {
    return reverse;
  }

  public void setReverse(boolean reverse) {
    this.reverse = reverse;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameUsageRequest)) return false;
    NameUsageRequest that = (NameUsageRequest) o;
    return fuzzy == that.fuzzy &&
      Objects.equals(q, that.q) &&
      Arrays.equals(searchTerms, that.searchTerms) &&
      minRank == that.minRank &&
      maxRank == that.maxRank &&
      sortBy == that.sortBy &&
      reverse == that.reverse;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(q, fuzzy, minRank, maxRank, sortBy, reverse);
    result = 31 * result + Arrays.hashCode(searchTerms);
    return result;
  }
}
