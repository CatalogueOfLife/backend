package org.col.api.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.col.api.util.VocabularyUtils;

public class NameSearchRequest {

  public static enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public static enum SortBy {
    NATIVE, NAME, TAXONOMIC
  }

  /**
   * Symbolic value to be used to indicate an IS NOT NULL document search.
   */
  public static final String NOT_NULL_VALUE = "_NOT_NULL";

  /**
   * Symbolic value to be used to indicate an IS NULL document search.
   */
  public static final String NULL_VALUE = "_NULL";

  private EnumMap<NameSearchParameter, List<Object>> filters;

  @QueryParam("facet")
  private Set<NameSearchParameter> facets;

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

  public NameSearchRequest() {}

  /**
   * Creates a shallow copy of this NameSearchRequest. The filters map is copied using EnumMap's copy constructor. Therefore you should not
   * manipulate the filter values (which are lists) as they are copied by reference. You can, however, add/remove filters, facets and search
   * content.
   */
  public NameSearchRequest copy() {
    NameSearchRequest copy = new NameSearchRequest();
    copy.filters = new EnumMap<>(getFilters());
    copy.facets = EnumSet.copyOf(getFacets());
    copy.content = EnumSet.copyOf(getContent());
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
  public void addQueryParams(MultivaluedMap<String, String> params) {
    for (Map.Entry<String, List<String>> param : params.entrySet()) {
      VocabularyUtils.lookup(param.getKey(), NameSearchParameter.class).ifPresent(p -> {
        addFilter(p, param.getValue());
      });
    }
  }

  public void addFilter(NameSearchParameter param, Iterable<?> values) {
    values.forEach((s) -> addFilter(param, s == null ? NULL_VALUE : s.toString()));
  }

  public void addFilter(NameSearchParameter param, Object... values) {
    Arrays.stream(values).forEach((v) -> addFilter(param, v == null ? NULL_VALUE : v.toString()));
  }

  /*
   * Primary usage case - parameter values coming in as strings from the HTTP request. Values are validated and converted to the type
   * associated with the parameter.
   */
  public void addFilter(NameSearchParameter param, String value) {
    value = StringUtils.trimToNull(value);
    if (value == null || value.equals(NULL_VALUE)) {
      addFilterValue(param, NULL_VALUE);
    } else if (value.equals(NOT_NULL_VALUE)) {
      addFilterValue(param, NOT_NULL_VALUE);
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

  public void addFilter(NameSearchParameter param, Integer value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, value.toString());
  }

  public void addFilter(NameSearchParameter param, Enum<?> value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, String.valueOf(value.ordinal()));
  }

  public void addFilter(NameSearchParameter param, UUID value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    addFilter(param, String.valueOf(value));
  }

  private void addFilterValue(NameSearchParameter param, Object value) {
    List<Object> values = getFilters().get(param);
    if (values == null) {
      values = new ArrayList<>();
      filters.put(param, values);
    }
    values.add(value);
  }

  public List<Object> getFilterValue(NameSearchParameter param) {
    return getFilters().get(param);
  }

  public boolean hasFilter(NameSearchParameter filter) {
    return getFilters().containsKey(filter);
  }

  public List<Object> removeFilter(NameSearchParameter filter) {
    return getFilters().remove(filter);
  }

  public void addFacet(NameSearchParameter facet) {
    getFacets().add(facet);
  }

  public EnumMap<NameSearchParameter, List<Object>> getFilters() {
    if (filters == null) {
      return (filters = new EnumMap<>(NameSearchParameter.class));
    }
    return filters;
  }

  public Set<NameSearchParameter> getFacets() {
    if (facets == null) {
      return (facets = EnumSet.noneOf(NameSearchParameter.class));
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NameSearchRequest that = (NameSearchRequest) o;
    return Objects.equals(content, that.content)
        && Objects.equals(facets, that.facets)
        && Objects.equals(filters, that.filters)
        && Objects.equals(q, that.q)
        && sortBy == that.sortBy
        && highlight == that.highlight
        && reverse == that.reverse;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), content, facets, filters, q, sortBy, highlight, reverse);
  }

  private static IllegalArgumentException illegalValueForParameter(NameSearchParameter param, String value) {
    String err = String.format("Illegal value for parameter %s: %s", param, value);
    return new IllegalArgumentException(err);
  }

}
