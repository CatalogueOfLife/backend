package life.catalogue.api.search;

import static life.catalogue.api.util.VocabularyUtils.lookupEnum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import life.catalogue.api.util.VocabularyUtils;

public class NameUsageSearchRequest {

  public static enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public static enum SortBy {
    NATIVE, NAME, TAXONOMIC
  }

  /**
   * Symbolic value to be used to indicate an IS NOT NULL document search.
   */
  public static final String IS_NOT_NULL = "_NOT_NULL";

  /**
   * Symbolic value to be used to indicate an IS NULL document search.
   */
  public static final String IS_NULL = "_NULL";

  private EnumMap<NameUsageSearchParameter, List<Object>> filters;

  @QueryParam("facet")
  private Set<NameUsageSearchParameter> facets;

  @QueryParam("content")
  private Set<SearchContent> content;

  @QueryParam("q")
  private String q;

  @QueryParam("sortBy")
  private SortBy sortBy;

  @QueryParam("highlight")
  private boolean highlight;

  @QueryParam("reverse")
  private boolean reverse;

  private String[] searchTerms;

  public NameUsageSearchRequest() {}

  /**
   * Creates a shallow copy of this NameSearchRequest. The filters map is copied using EnumMap's copy constructor. Therefore you should not
   * manipulate the filter values (which are lists) as they are copied by reference. You can, however, add/remove filters, facets and search
   * content.
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
    return copy;
  }

  /**
   * Extracts all query parameters that match a NameSearchParameter and registers them as query filters. Values of query parameters that are
   * associated with an enum type can be supplied using the name of the enum constant or using the ordinal of the enum constant. In both
   * cases it is the ordinal that will be registered as the query filter.
   */
  public void addFilters(MultivaluedMap<String, String> params) {
    Set<String> nonFilters = new HashSet<String>(Arrays.asList(
        "offset",
        "limit",
        "facet",
        "content",
        "q",
        "sortBy",
        "highlight",
        "reverse"));
    params.entrySet().stream().filter(e -> !nonFilters.contains(e.getKey())).forEach(e -> {
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

  public void addFilter(NameUsageSearchParameter param, Integer value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, value.toString());
  }

  public void addFilter(NameUsageSearchParameter param, Enum<?> value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, String.valueOf(value.ordinal()));
  }

  public void addFilter(NameUsageSearchParameter param, UUID value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, String.valueOf(value));
  }

  private void addFilterValue(NameUsageSearchParameter param, Object value) {
    getFilters().computeIfAbsent(param, k -> new ArrayList<>()).add(value);
  }

  @SuppressWarnings("unchecked")
  public <T> List<T> getFilterValues(NameUsageSearchParameter param) {
    return (List<T>) getFilters().get(param);
  }

  @SuppressWarnings("unchecked")
  public <T> T getFilterValue(NameUsageSearchParameter param) {
    if (hasFilter(param)) {
      return (T) getFilters().get(param).get(0);
    }
    return null;
  }

  public boolean hasQ() {
    return StringUtils.isNotBlank(q);
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

  public EnumMap<NameUsageSearchParameter, List<Object>> getFilters() {
    if (filters == null) {
      return (filters = new EnumMap<>(NameUsageSearchParameter.class));
    }
    return filters;
  }

  public Set<NameUsageSearchParameter> getFacets() {
    if (facets == null) {
      return (facets = EnumSet.noneOf(NameUsageSearchParameter.class));
    }
    return facets;
  }

  public Set<SearchContent> getContent() {
    if (content == null || content.isEmpty()) {
      return (content = EnumSet.allOf(SearchContent.class));
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

  public String getQ() {
    return q;
  }

  public void setQ(String q) {
    this.q = q;
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

  @JsonIgnore
  public String[] getSearchTerms() {
    return searchTerms;
  }

  public void setSearchTerms(String[] searchTerms) {
    this.searchTerms = searchTerms;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return content == null
        && (facets == null || facets.isEmpty())
        && (filters == null || filters.isEmpty())
        && q == null
        && sortBy == null
        && highlight == false
        && reverse == false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(content, facets, filters, highlight, q, reverse, sortBy);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameUsageSearchRequest other = (NameUsageSearchRequest) obj;
    return Objects.equals(content, other.content) && Objects.equals(facets, other.facets)
        && Objects.equals(filters, other.filters) && highlight == other.highlight && Objects.equals(q, other.q) && reverse == other.reverse
        && sortBy == other.sortBy;
  }

  private static IllegalArgumentException illegalValueForParameter(NameUsageSearchParameter param, String value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

}
