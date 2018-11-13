package org.col.api.search;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.col.api.util.VocabularyUtils;

/*
 * Making this class extends MultiValuedHashMap causes Jackson to ony serialize the superclass (MultiValuedHashMap), not the properties of
 * NameSearchRequest itself (q, factets, etc). Can probably be rectified using a JsonSerializer, but including the map as a property feels
 * natural anyhow: getQ(), getFacets(), getFilters().
 */
public class NameSearchRequest {

  public static enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public static enum SortBy {
    NATIVE, NAME, TAXONOMIC
  }

  /**
   * Value to be used to indicate an IS NOT NULL document search.
   */
  public static final String NOT_NULL_VALUE = "_NOT_NULL";

  /**
   * Value to be used to indicate an IS NULL document search.
   */
  public static final String NULL_VALUE = "_NULL";

  private MultivaluedHashMap<NameSearchParameter, String> filters;

  @QueryParam("facet")
  private Set<NameSearchParameter> facets;

  @QueryParam("content")
  private Set<SearchContent> content;

  @QueryParam("q")
  private String q;

  @QueryParam("sortBy")
  private SortBy sortBy;

  /**
   * Whether or not the value in the request has any special meaning or is to be taken at face value.
   */
  public static boolean isLiteral(CharSequence value) {
    return !StringUtils.isBlank(value) && !value.equals(NOT_NULL_VALUE) && !value.equals(NULL_VALUE);
  }

  /**
   * Extracts all query parameters that match a NameSearchParameter and adds them as request filters.
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

  public void addFilter(NameSearchParameter param, Object[] values) {
    Arrays.stream(values).forEach((v) -> addFilter(param, v == null ? NULL_VALUE : v.toString()));
  }

  public void addFilter(NameSearchParameter param, int[] values) {
    Arrays.stream(values).forEach((v) -> addFilter(param, String.valueOf(v)));
  }

  public void addFilter(NameSearchParameter param, String value) {
    value = StringUtils.trimToNull(value);
    if (value == null || value.equals(NULL_VALUE)) {
      add(param, NULL_VALUE);
    } else if (value.equals(NOT_NULL_VALUE)) {
      add(param, NOT_NULL_VALUE);
    } else if (param.isLegalValue(value)) {
      add(param, value);
    } else {
      String err = String.format("Illegal value for parameter %s: %s", param, value);
      throw new IllegalArgumentException(err);
    }
  }

  public void addFilter(NameSearchParameter param, Enum<?> value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    if (value.getClass() != param.type()) {
      String err = String.format("Incompatible types: %s, %s", param.type().getSimpleName(), value.getClass().getSimpleName());
      throw new IllegalArgumentException(err);
    }
    add(param, value.name());
  }

  public void addFilter(NameSearchParameter param, Integer value) {
    Preconditions.checkNotNull(value, "Null values not allowed for non-strings");
    if (param.type() == Integer.class || param.type() == String.class) {
      add(param, value.toString());
    } else {
      String err = String.format("Incompatible types: %s, %s", param.type().getSimpleName(), value.getClass().getSimpleName());
      throw new IllegalArgumentException(err);
    }
  }

  private void add(NameSearchParameter param, String value) {
    if (filters == null) {
      filters = new MultivaluedHashMap<>();
    }
    filters.add(param, value);
  }

  public List<String> get(NameSearchParameter param) {
    if (filters == null) {
      return null;
    }
    return filters.get(param);
  }

  public int size() {
    return filters == null ? 0 : filters.size();
  }

  public boolean containsKey(NameSearchParameter filter) {
    return filters == null ? false : filters.containsKey(filter);
  }

  public void addFacet(NameSearchParameter facet) {
    if (facets == null) {
      facets = new LinkedHashSet<>();
    }
    facets.add(facet);
  }

  public MultivaluedHashMap<NameSearchParameter, String> getFilters() {
    return filters;
  }

  public Set<NameSearchParameter> getFacets() {
    return facets;
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

  public Set<SearchContent> getContent() {
    return content;
  }

  public void setContent(Set<SearchContent> content) {
    this.content = content;
  }

  @JsonIgnore
  public boolean isEmpty() {
    return q == null && (filters == null || filters.isEmpty()) && (facets == null || facets.isEmpty());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NameSearchRequest that = (NameSearchRequest) o;
    return Objects.equals(content, that.content) && Objects.equals(facets, that.facets) && Objects.equals(q, that.q)
        && sortBy == that.sortBy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), content, facets, q, sortBy);
  }

}
