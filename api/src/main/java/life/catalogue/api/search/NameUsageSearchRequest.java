
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
    NAME, TAXONOMIC, INDEX_NAME_ID
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

  @QueryParam("sortBy")
  private SortBy sortBy = SortBy.TAXONOMIC;

  @QueryParam("highlight")
  private boolean highlight;

  @QueryParam("reverse")
  private boolean reverse;

  @QueryParam("prefix")
  private boolean prefix;

  @QueryParam("minRank")
  private Rank minRank;

  @QueryParam("maxRank")
  private Rank maxRank;

  public NameUsageSearchRequest() {}

  @JsonCreator
  public NameUsageSearchRequest(@JsonProperty("filter") Map<NameUsageSearchParameter, @Size(max = 1000) Set<Object>> filters,
      @JsonProperty("facet") Set<NameUsageSearchParameter> facets,
      @JsonProperty("content") Set<SearchContent> content,
      @JsonProperty("sortBy") SortBy sortBy,
      @JsonProperty("q") String q,
      @JsonProperty("highlight") boolean highlight,
      @JsonProperty("reverse") boolean reverse,
      @JsonProperty("fuzzy") boolean fuzzy,
      @JsonProperty("prefix") boolean prefix,
      @JsonProperty("minRank") Rank minRank,
      @JsonProperty("maxRank") Rank maxRank) {
    this.filters = filters == null ? new EnumMap<>(NameUsageSearchParameter.class) : new EnumMap<>(filters);
    this.facets = facets == null ? EnumSet.noneOf(NameUsageSearchParameter.class) : EnumSet.copyOf(facets);
    this.content = content == null ? EnumSet.noneOf(SearchContent.class) : EnumSet.copyOf(content);
    setQ(q); // see comments there
    this.sortBy = sortBy;
    this.highlight = highlight;
    this.reverse = reverse;
    this.fuzzy = fuzzy;
    this.prefix = prefix;
    this.minRank = minRank;
    this.maxRank = maxRank;
  }

  /**
   * Creates a shallow copy of this NameSearchRequest. The filters map is copied using EnumMap's copy constructor. Therefore you should not
   * manipulate the filter values (which are lists) as they are copied by reference. You can, however, simply replace the list with another
   * list, and you can also add/remove facets and search content without affecting the original request.
   */
  public NameUsageSearchRequest copy() {
    NameUsageSearchRequest copy = new NameUsageSearchRequest();
    if (filters != null) {
      copy.filters = new EnumMap<>(NameUsageSearchParameter.class);
      copy.filters.putAll(filters);
    }
    if (facets != null) {
      copy.facets = EnumSet.noneOf(NameUsageSearchParameter.class);
      copy.facets.addAll(facets);
    }
    if (content != null) {
      copy.content = EnumSet.noneOf(SearchContent.class);
      copy.content.addAll(content);
    }
    copy.q = q;
    copy.sortBy = sortBy;
    copy.highlight = highlight;
    copy.reverse = reverse;
    copy.fuzzy = fuzzy;
    copy.prefix = prefix;
    copy.minRank = minRank;
    copy.maxRank = maxRank;
    return copy;
  }

  /**
   * Extracts all query parameters that match a NameSearchParameter and registers them as query filters. Values of query parameters that are
   * associated with an enum type can be supplied using the name of the enum constant or using the ordinal of the enum constant. In both
   * cases it is the ordinal that will be registered as the query filter.
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

  /*
   * Primary usage case - parameter values coming in as strings from the HTTP request. Values are validated and converted to the type
   * associated with the parameter.
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
        && sortBy == null
        && !highlight
        && !reverse
        && !fuzzy
        && !prefix;
  }

  private static void nonNull(Object value){
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
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

  public void setContent(Set<SearchContent> content) {
    if (content == null || content.size() == 0) {
      this.content = EnumSet.allOf(SearchContent.class);
    } else {
      this.content = content;
    }
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

  /**
   * Whether or not to match on whole words only.
   */
  public boolean isPrefix() {
    return prefix;
  }

  public void setPrefix(boolean prefix) {
    this.prefix = prefix;
  }

  public Rank getMinRank() {
    return minRank;
  }

  public void setMinRank(Rank minRank) {
    this.minRank = minRank;
  }

  /**
   * Filters usages by their rank, excluding all usages with a higher rank then the one given.
   * E.g. maxRank=FAMILY will include usages of rank family and below (genus, species, etc), but exclude all orders and above.
   */
  public Rank getMaxRank() {
    return maxRank;
  }

  public void setMaxRank(Rank maxRank) {
    this.maxRank = maxRank;
  }

  private static IllegalArgumentException illegalValueForParameter(NameUsageSearchParameter param, String value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(content, facets, filters, highlight, reverse, sortBy, prefix, minRank, maxRank);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    NameUsageSearchRequest other = (NameUsageSearchRequest) obj;
    return Objects.equals(content, other.content) &&
        Objects.equals(facets, other.facets) &&
        Objects.equals(filters, other.filters) &&
        highlight == other.highlight &&
        reverse == other.reverse &&
        sortBy == other.sortBy &&
        prefix == other.prefix &&
        minRank == other.minRank &&
        maxRank == other.maxRank;
  }

}
