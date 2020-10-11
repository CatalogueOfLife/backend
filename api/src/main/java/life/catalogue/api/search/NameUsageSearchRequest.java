
package life.catalogue.api.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
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

  public enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP
  }

  static final Set<SearchContent> DEFAULT_CONTENT = Sets.immutableEnumSet(SearchContent.SCIENTIFIC_NAME, SearchContent.AUTHORSHIP);

  public enum SortBy {
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

  private EnumMap<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters = new EnumMap<>(NameUsageSearchParameter.class);

  @QueryParam("facet")
  private Set<NameUsageSearchParameter> facets = EnumSet.noneOf(NameUsageSearchParameter.class);

  @QueryParam("content")
  private Set<SearchContent> content = EnumSet.copyOf(DEFAULT_CONTENT);

  @QueryParam("highlight")
  private boolean highlight;

  @QueryParam("type")
  private SearchType searchType;

  public NameUsageSearchRequest() {}

  public NameUsageSearchRequest(NameUsageSearchRequest.SearchContent content) {
    setSingleContent(content);
  }

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
    super(q, fuzzy, minRank, maxRank, sortBy, reverse);
    this.highlight = highlight;
    this.fuzzy = fuzzy;
    this.searchType = searchType;
    setFilters(filters);
    setFacets(facets);
    setContent(content);
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
    setFilters(other.filters);
    setFacets(other.facets);
    setContent(other.content);
    this.highlight = other.highlight;
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
        && !highlight
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
    return filters != null && getFilters().containsKey(filter);
  }

  public void removeFilter(NameUsageSearchParameter filter) {
    if (filters != null) {
      filters.remove(filter);
    }
  }

  public void setFacets(Set<NameUsageSearchParameter> facets) {
    this.facets = facets == null || facets.isEmpty() ? EnumSet.noneOf(NameUsageSearchParameter.class) : EnumSet.copyOf(facets);
  }

  public void addFacet(NameUsageSearchParameter facet) {
    getFacets().add(facet);
  }

  public void setFilters(Map<NameUsageSearchParameter, Set<Object>> filters) {
    this.filters = filters == null || filters.isEmpty() ? new EnumMap<>(NameUsageSearchParameter.class) : new EnumMap<>(filters);
  }

  public Map<NameUsageSearchParameter, Set<Object>> getFilters() {
    return filters;
  }

  public Set<NameUsageSearchParameter> getFacets() {
    return facets;
  }

  public Set<SearchContent> getContent() {
    return content;
  }

  public void setSingleContent(SearchContent content) {
    this.content = content == null ? EnumSet.copyOf(DEFAULT_CONTENT) : EnumSet.of(content);
  }

  public void setContent(Set<SearchContent> content) {
    if (content == null || content.isEmpty()) {
      setContentDefault();
    } else {
      this.content = EnumSet.copyOf(content);
    }
  }

  public void setContentDefault() {
    this.content = EnumSet.copyOf(DEFAULT_CONTENT);
  }

  public boolean isHighlight() {
    return highlight;
  }

  public void setHighlight(boolean highlight) {
    this.highlight = highlight;
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
    return highlight == that.highlight &&
      Objects.equals(filters, that.filters) &&
      Objects.equals(facets, that.facets) &&
      Objects.equals(content, that.content) &&
      searchType == that.searchType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), filters, facets, content, highlight, searchType);
  }
}
