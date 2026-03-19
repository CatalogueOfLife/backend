package life.catalogue.api.search;

import life.catalogue.api.util.VocabularyUtils;

import org.gbif.nameparser.api.Rank;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

import jakarta.validation.constraints.Size;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MultivaluedMap;

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
  public enum SearchType {
    /**
     * Matches a search term to the beginning of the words of a scientific name. This is the only
     * available search type for the suggest service. Whole-word and exact matching defies the purpose
     * of auto-completion.
     */
    PREFIX,

    /**
     * Matches a search term to entire epithets within a scientific name.
     */
    WHOLE_WORDS,

    /**
     * Matches the entire search phrase to the entire scientific name.
     */
    EXACT,

    /**
     * Matches the search phrase to the entire scientific name using a fuzzy algorithm.
     */
    FUZZY
  }

  public enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public enum SortBy {
    NAME, TAXONOMIC, RELEVANCE
  }

  @QueryParam("q")
  protected String q;

  @QueryParam("minRank")
  private Rank minRank;

  @QueryParam("maxRank")
  private Rank maxRank;

  @QueryParam("sortBy")
  private SortBy sortBy;

  @QueryParam("reverse")
  private boolean reverse;

  private EnumMap<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters = new EnumMap<>(NameUsageSearchParameter.class);


  public NameUsageRequest() {
  }

  public NameUsageRequest(String q, Rank minRank, Rank maxRank, NameUsageSearchRequest.SortBy sortBy, boolean reverse) {
    this.q = q;
    this.minRank = minRank;
    this.maxRank = maxRank;
    this.sortBy = sortBy;
    this.reverse = reverse;
  }

  public NameUsageRequest(NameUsageRequest other) {
    this.q = other.q;
    this.minRank = other.minRank;
    this.maxRank = other.maxRank;
    this.sortBy = other.sortBy;
    this.reverse = other.reverse;
    setFilters(other.filters);
  }

  public void clearFilter(NameUsageSearchParameter param) {
    getFilters().remove(param);
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
    this.filters = new EnumMap<>(NameUsageSearchParameter.class);
    if (filters != null) {
      for (Map.Entry<NameUsageSearchParameter, Set<Object>> e : filters.entrySet()) {
        NameUsageSearchParameter p = e.getKey();
        Set<Object> vals = e.getValue();
        if (vals != null) {
          this.filters.put(p,
            new HashSet<>(vals.stream().map(v -> toParamType(p,v)).toList())
          );
        }
      }
    }
  }

  private Object toParamType(NameUsageSearchParameter param, Object value) {
    if (value instanceof String) {
      value = StringUtils.trimToNull(value.toString());
    }
    if (value == null || value.equals(IS_NULL)) {
      return IS_NULL;
    } else if (value.equals(IS_NOT_NULL)) {
      return IS_NOT_NULL;
    } else if (param.type() == value.getClass()) {
      return value;
    } else if (param.type() == String.class) {
      return value.toString();
    } else if (param.type() == UUID.class) {
      try {
        return UUID.fromString(value.toString());
      } catch (IllegalArgumentException e) {
        throw illegalValueForParameter(param, value);
      }
    } else if (param.type() == Integer.class) {
      try {
        return Integer.valueOf(value.toString());
      } catch (NumberFormatException e) {
        throw illegalValueForParameter(param, value);
      }
    } else if (param.type() == Boolean.class) {
      if (value.equals("-1") || value.equals("0") || value.toString().equalsIgnoreCase("f") || value.toString().equalsIgnoreCase("false")) {
        return false;
      } else if (value.equals("1") || value.toString().equalsIgnoreCase("t") || value.toString().equalsIgnoreCase("true")) {
        return true;
      } else {
        throw illegalValueForParameter(param, value);
      }
    } else if (param.type().isEnum()) {
      try {
        int i;
        if (value.getClass() == Integer.class) {
          i = (int) value;
        } else {
          i = Integer.parseInt(value.toString());
        }
        if (i < 0 || i >= param.type().getEnumConstants().length) {
          throw illegalValueForParameter(param, value);
        }
        return param.type().getEnumConstants()[i];
      } catch (NumberFormatException e) {
        //noinspection unchecked
        return VocabularyUtils.lookupEnum(value.toString(), (Class<? extends Enum<?>>) param.type());
      }
    } else {
      throw new IllegalArgumentException("Unexpected parameter type: " + param.type());
    }
  }

  public Map<NameUsageSearchParameter, Set<Object>> getFilters() {
    return filters;
  }

  public abstract SearchType getSearchType();

  public abstract Set<NameUsageSearchRequest.SearchContent> getContent();

  /**
   * Extracts all query parameters that match a NameSearchParameter and registers them as query
   * filters. Values of query parameters that are associated with an enum type can be supplied using
   * the name of the enum constant or using the ordinal of the enum constant. In both cases it is the
   * ordinal that will be registered as the query filter.
   */
  public void addFilters(MultivaluedMap<String, String> params) {
    params.entrySet().stream().filter(e -> !NON_FILTERS.contains(e.getKey())).forEach(e -> {
      NameUsageSearchParameter p = lookupEnum(e.getKey(), NameUsageSearchParameter.class); // Allow IllegalArgumentException
      addFilter(p, e.getValue());
    });
  }

  public void addFilter(NameUsageSearchParameter param, Iterable<?> values) {
    values.forEach(v -> addFilter(param, toParamType(param,v)));
  }

  public void addFilter(NameUsageSearchParameter param, Object... values) {
    Arrays.stream(values).forEach(v -> addFilterValue(param, toParamType(param,v)));
  }

  private void addFilterValue(NameUsageSearchParameter param, Object value) {
    getFilters().computeIfAbsent(param, k -> new HashSet<>()).add(value);
  }

  public void setDatasetFilter(int datasetKey) {
    setFilter(NameUsageSearchParameter.DATASET_KEY, String.valueOf(datasetKey));
  }

  /**
   * Sets a single filter, removing any existing filter for the given parameter.
   */
  public void setFilter(NameUsageSearchParameter param, Object value) {
    getFilters().remove(param);
    addFilter(param, value);
  }

  private static void nonNull(Object value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
  }

  private static IllegalArgumentException illegalValueForParameter(NameUsageSearchParameter param, Object value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return q == null
      && minRank==null
      && maxRank==null
      && sortBy == null
      && !reverse;
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
    if (!(o instanceof NameUsageRequest that)) return false;

    return reverse == that.reverse &&
      Objects.equals(q, that.q) &&
      minRank == that.minRank &&
      maxRank == that.maxRank &&
      sortBy == that.sortBy &&
      Objects.equals(filters, that.filters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, minRank, maxRank, sortBy, reverse, filters);
  }
}
