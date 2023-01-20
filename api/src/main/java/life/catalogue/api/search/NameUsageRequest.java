package life.catalogue.api.search;

import life.catalogue.api.util.VocabularyUtils;

import org.gbif.nameparser.api.Rank;

import java.lang.reflect.Field;
import java.util.*;

import javax.validation.constraints.Size;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

import static life.catalogue.api.util.VocabularyUtils.lookupEnum;

/**
 * Base class for {@link NameUsageSearchRequest} and {@link NameUsageSuggestRequest}.
 * 
 */
public abstract class NameUsageRequest {

  /**
   * Symbolic value to be used to indicate an IS NOT NULL document search.
   */
  public static final String IS_NOT_NULL = "_NOT_NULL";
  /**
   * Symbolic value to be used to indicate an IS NULL document search.
   */
  public static final String IS_NULL = "_NULL";

  /**
   * http query parameters that are true request parameters, but should not considered to be filters.
   */
  private static final Set<String> NON_FILTERS;

  static {
    Set<String> non = new HashSet<>();
    // for paging
    non.add("limit");
    non.add("offset");
    // search request classes
    for (Class requestClass : List.of(NameUsageRequest.class, NameUsageSearchRequest.class, NameUsageSuggestRequest.class)) {
      for (Field f : FieldUtils.getFieldsWithAnnotation(requestClass, QueryParam.class)) {
        for (QueryParam qp : f.getAnnotationsByType(QueryParam.class)) {
          non.add(qp.value());
        }
      }
    }
    NON_FILTERS = Set.copyOf(non);
  }

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

  private EnumMap<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters = new EnumMap<>(NameUsageSearchParameter.class);

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
    setFilters(other.filters);
  }

  public void clearFilter(NameUsageSearchParameter param) {
    getFilters().remove(param);
  }

  public void addFilter(NameUsageSearchParameter param, Integer value) {
    nonNull(value);
    addFilter(param, value.toString());
  }

  public void addFilter(NameUsageSearchParameter param, Enum<?> value) {
    nonNull(value);
    addFilter(param, value.name());
  }

  public void addFilter(NameUsageSearchParameter param, UUID value) {
    nonNull(value);
    addFilter(param, String.valueOf(value));
  }

  private void addFilterValue(NameUsageSearchParameter param, Object value) {
    getFilters().computeIfAbsent(param, k -> new LinkedHashSet<>()).add(value);
  }

  /**
   * Returns all values for the provided query parameter.
   * 
   * @param <T>
   * @param param
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> Set<T> getFilterValues(NameUsageSearchParameter param) {
    return (Set<T>) getFilters().get(param);
  }

  /**
   * Retuns the first value of the provided query parameter.
   * 
   * @param <T>
   * @param param
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T getFilterValue(NameUsageSearchParameter param) {
    if (hasFilter(param)) {
      return (T) getFilters().get(param).iterator().next();
    }
    return null;
  }

  public boolean hasFilters() {
    return filters != null && !filters.isEmpty();
  }

  public boolean hasFilter(NameUsageSearchParameter filter) {
    return filters != null && getFilters().containsKey(filter);
  }

  public void removeFilter(NameUsageSearchParameter filter) {
    if (filters != null) {
      filters.remove(filter);
    }
  }

  public void setFilters(Map<NameUsageSearchParameter, Set<Object>> filters) {
    this.filters = filters == null || filters.isEmpty() ? new EnumMap<>(NameUsageSearchParameter.class) : new EnumMap<>(filters);
  }

  public Map<NameUsageSearchParameter, Set<Object>> getFilters() {
    return filters;
  }

  public abstract SearchType getSearchType();

  /**
   * Extracts all query parameters that match a NameSearchParameter and registers them as query
   * filters. Values of query parameters that are associated with an enum type can be supplied using
   * the name of the enum constant or using the ordinal of the enum constant. In both cases it is the
   * name that will be registered as the query filter.
   */
  public void addFilters(MultivaluedMap<String, String> params) {
    params.entrySet().stream().filter(e -> !NON_FILTERS.contains(e.getKey())).forEach(e -> {
      NameUsageSearchParameter p = lookupEnum(e.getKey(), NameUsageSearchParameter.class); // Allow IllegalArgumentException
      addFilter(p, e.getValue());
    });
  }

  public void addFilter(NameUsageSearchParameter param, Iterable<?> values) {
    values.forEach(v -> addFilter(param, v == null ? IS_NULL : v.toString()));
  }

  public void addFilter(NameUsageSearchParameter param, Object... values) {
    Arrays.stream(values).forEach(v -> addFilter(param, v == null ? IS_NULL : v.toString()));
  }

  public void setDatasetFilter(int datasetKey) {
    setFilter(NameUsageSearchParameter.DATASET_KEY, String.valueOf(datasetKey));
  }

  /**
   * Sets a single filter, removing any existing filter for the given parameter.
   */
  public void setFilter(NameUsageSearchParameter param, String value) {
    getFilters().remove(param);
    addFilter(param, value);
  }

  /*
   * Primary usage case - parameter values coming in as strings from the HTTP request. Values are
   * validated and converted to the type associated with the parameter.
   */
  public void addFilter(NameUsageSearchParameter param, String value) {
    value = StringUtils.trimToNull(value);
    if (value == null || value.equals(IS_NULL)) {
      addFilterValue(param, IS_NULL);
    } else if (value.equals(IS_NOT_NULL)) {
      addFilterValue(param, IS_NOT_NULL);
    } else if (param.type() == String.class) {
      addFilterValue(param, value);
    } else if (param.type() == UUID.class) {
      addFilterValue(param, value);
    } else if (param.type() == Integer.class) {
      try {
        Integer i = Integer.valueOf(value);
        addFilterValue(param, i);
      } catch (NumberFormatException e) {
        throw illegalValueForParameter(param, value);
      }
    } else if (param.type() == Boolean.class) {
      if (value.equals("-1") || value.equals("0") || value.toLowerCase().equals("f") || value.toLowerCase().equals("false")) {
        addFilterValue(param, false);
      } else if (value.equals("1") || value.toLowerCase().equals("t") || value.toLowerCase().equals("true")) {
        addFilterValue(param, true);
      } else {
        throw illegalValueForParameter(param, value);
      }
    } else if (param.type().isEnum()) {
        Enum<?> c = VocabularyUtils.lookupEnum(value, (Class<? extends Enum<?>>) param.type());
        addFilterValue(param, c.name());
    } else {
      throw new IllegalArgumentException("Unexpected parameter type: " + param.type());
    }
  }

  private static void nonNull(Object value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
  }

  private static IllegalArgumentException illegalValueForParameter(NameUsageSearchParameter param, String value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

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
      reverse == that.reverse &&
      Objects.equals(filters, that.filters);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(q, fuzzy, minRank, maxRank, sortBy, reverse, filters);
    result = 31 * result + Arrays.hashCode(searchTerms);
    return result;
  }
}
