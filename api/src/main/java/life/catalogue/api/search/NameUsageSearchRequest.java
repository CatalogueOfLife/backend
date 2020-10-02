
package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import life.catalogue.api.util.VocabularyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.gbif.nameparser.api.Rank;

import javax.validation.constraints.Size;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
import java.lang.reflect.Field;
import java.util.*;

import static life.catalogue.api.util.VocabularyUtils.lookupEnum;

public class NameUsageSearchRequest extends NameUsageRequest {

  private static final Set<String> NON_FILTERS;

  static {
    Set<String> non = new HashSet<>();
    // for paging
    non.add("limit");
    non.add("offset");
    // search request itself
    for (Field f : FieldUtils.getFieldsWithAnnotation(NameUsageSearchRequest.class, QueryParam.class)) {
      for (QueryParam qp : f.getAnnotationsByType(QueryParam.class)) {
        non.add(qp.value());
      }
    }
    NON_FILTERS = Set.copyOf(non);
  }

  public static enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public static enum SortBy {
    NAME, TAXONOMIC, INDEX_NAME_ID, NATIVE, RELEVANCE
  }

  /**
   * Symbolic value to be used to indicate an IS NOT NULL document search.
   */
  public static final String IS_NOT_NULL = "_NOT_NULL";

  /**
   * Symbolic value to be used to indicate an IS NULL document search.
   */
  public static final String IS_NULL = "_NULL";

  private EnumMap<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters;

  @QueryParam("facet")
  private Set<NameUsageSearchParameter> facets;

  @QueryParam("content")
  private Set<SearchContent> content;

  /**
   * Whether to include vernacular names in the response. Defaults to false
   */
  @QueryParam("vernacular")
  private boolean vernacular = false;

  @QueryParam("sortBy")
  private SortBy sortBy;

  @QueryParam("highlight")
  private boolean highlight;

  @QueryParam("reverse")
  private boolean reverse;

  @QueryParam("type")
  private SearchType searchType;

  public NameUsageSearchRequest() {}

  @JsonCreator
  public NameUsageSearchRequest(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters,
      @JsonProperty("facet") Set<NameUsageSearchParameter> facets,
      @JsonProperty("content") Set<SearchContent> content,
      @JsonProperty("vernacular") boolean vernacular,
      @JsonProperty("sortBy") SortBy sortBy,
      @JsonProperty("q") String q,
      @JsonProperty("highlight") boolean highlight,
      @JsonProperty("reverse") boolean reverse,
      @JsonProperty("fuzzy") boolean fuzzy,
      @JsonProperty("type") SearchType searchType,
      @JsonProperty("minRank") Rank minRank,
      @JsonProperty("maxRank") Rank maxRank) {
    super();
    this.filters = filters == null || filters.isEmpty() ? new EnumMap<>(NameUsageSearchParameter.class) : new EnumMap<>(filters);
    this.facets = facets == null || facets.isEmpty() ? EnumSet.noneOf(NameUsageSearchParameter.class) : EnumSet.copyOf(facets);
    this.content = content == null || content.isEmpty() ? EnumSet.noneOf(SearchContent.class) : EnumSet.copyOf(content);
    this.vernacular = vernacular;
    this.sortBy = sortBy;
    this.highlight = highlight;
    this.reverse = reverse;
    this.fuzzy = fuzzy;
    this.searchType = searchType;
    setQ(q); // see comments there
    setMinRank(minRank);
    setMaxRank(maxRank);
  }

  /**
   * Creates a nearly deep copy of this NameSearchRequest, but does deep copy the filters map values!
   * The filters map is copied using EnumMap's copy constructor.
   * Therefore you should not manipulate the filter values (which are lists) as they are
   * copied by reference. You can, however, simply replace the list with another list, and you can
   * also add/remove facets and search content without affecting the original request.
   */
  public NameUsageSearchRequest(NameUsageSearchRequest other) {
    super(other);
    this.filters = other.filters == null ? null : new EnumMap<>(other.filters);
    this.facets = other.facets == null ? null : EnumSet.copyOf(other.facets);
    this.content = other.content == null ? null : EnumSet.copyOf(other.content);
    this.vernacular = other.vernacular;
    this.sortBy = other.sortBy;
    this.highlight = other.highlight;
    this.reverse = other.reverse;
    this.searchType = other.searchType;
  }



  /**
   * Creates a shallow copy of this NameSearchRequest. The filters map is copied using EnumMap's copy
   * constructor. Therefore you should not manipulate the filter values (which are lists) as they are
   * copied by reference. You can, however, simply replace the list with another list, and you can
   * also add/remove facets and search content without affecting the original request.
   */
  public NameUsageSearchRequest copy() {
    return new NameUsageSearchRequest(this);
  }

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
    values.forEach((s) -> addFilter(param, s == null ? IS_NULL : s.toString()));
  }

  public void addFilter(NameUsageSearchParameter param, Object... values) {
    Arrays.stream(values).forEach((v) -> addFilter(param, v == null ? IS_NULL : v.toString()));
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
      try {
        int i = Integer.parseInt(value);
        if (i < 0 || i >= param.type().getEnumConstants().length) {
          throw illegalValueForParameter(param, value);
        }
        addFilterValue(param, Integer.valueOf(i));
      } catch (NumberFormatException e) {
        @SuppressWarnings("unchecked")
        Enum<?> c = VocabularyUtils.lookupEnum(value, (Class<? extends Enum<?>>) param.type());
        addFilterValue(param, Integer.valueOf(c.ordinal()));
      }
    } else {
      throw new IllegalArgumentException("Unexpected parameter type: " + param.type());
    }
  }

  @JsonIgnore
  public boolean isEmpty() {
    return super.isEmpty() &&
        (content == null || content.isEmpty())
        && (facets == null || facets.isEmpty())
        && (filters == null || filters.isEmpty())
        && !vernacular
        && sortBy == null
        && !highlight
        && !reverse
        && !fuzzy
        && searchType == null;
  }

  private static void nonNull(Object value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
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
    addFilter(param, String.valueOf(value.ordinal()));
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
    return getFilters().containsKey(filter);
  }

  public void removeFilter(NameUsageSearchParameter filter) {
    if (filters != null) {
      filters.remove(filter);
    }
  }

  public void addFacet(NameUsageSearchParameter facet) {
    getFacets().add(facet);
  }

  public EnumMap<NameUsageSearchParameter, Set<Object>> getFilters() {
    if (filters == null) {
      filters = new EnumMap<>(NameUsageSearchParameter.class);
    }
    return filters;
  }

  public Set<NameUsageSearchParameter> getFacets() {
    if (facets == null) {
      facets = EnumSet.noneOf(NameUsageSearchParameter.class);
    }
    return facets;
  }

  public Set<SearchContent> getContent() {
    if (content == null || content.isEmpty()) {
      content = EnumSet.allOf(SearchContent.class);
    }
    return content;
  }

  public void setSingleContent(SearchContent content) {
    if (content == null) {
      this.content = EnumSet.allOf(SearchContent.class);
    } else {
      this.content = EnumSet.of(content);
    }
  }

  public void setContent(Set<SearchContent> content) {
    if (content == null || content.size() == 0) {
      this.content = EnumSet.allOf(SearchContent.class);
    } else {
      this.content = content;
    }
  }

  public boolean isVernacular() {
    return vernacular;
  }

  public void setVernacular(boolean vernacular) {
    this.vernacular = vernacular;
  }

  public SortBy getSortBy() {
    return sortBy;
  }

  public void setSortBy(SortBy sortBy) {
    this.sortBy = sortBy;
  }

  public boolean isHighlight() {
    return highlight;
  }

  public void setHighlight(boolean highlight) {
    this.highlight = highlight;
  }

  public boolean isReverse() {
    return reverse;
  }

  public void setReverse(boolean reverse) {
    this.reverse = reverse;
  }

  @Override
  public SearchType getSearchType() {
    return searchType;
  }

  public void setSearchType(SearchType searchType) {
    this.searchType = searchType;
  }

  private static IllegalArgumentException illegalValueForParameter(NameUsageSearchParameter param, String value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameUsageSearchRequest)) return false;
    if (!super.equals(o)) return false;
    NameUsageSearchRequest that = (NameUsageSearchRequest) o;
    return vernacular == that.vernacular &&
      highlight == that.highlight &&
      reverse == that.reverse &&
      Objects.equals(filters, that.filters) &&
      Objects.equals(facets, that.facets) &&
      Objects.equals(content, that.content) &&
      sortBy == that.sortBy &&
      searchType == that.searchType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), filters, facets, content, vernacular, sortBy, highlight, reverse, searchType);
  }
}
