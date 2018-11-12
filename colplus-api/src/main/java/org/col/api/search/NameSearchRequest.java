package org.col.api.search;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.col.api.util.VocabularyUtils;

public class NameSearchRequest extends MultivaluedHashMap<NameSearchParameter, String> {

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

  @QueryParam("content")
  private Set<SearchContent> content;

  @QueryParam("facet")
  private Set<NameSearchParameter> facets = new HashSet<>();

  @QueryParam("q")
  private String q;

  @QueryParam("sortBy")
  private SortBy sortBy;

  /**
   * Whether or not the value in the request has any special meaning or is to be taken at face value.
   */
  public static boolean isLiteral(String value) {
    return !StringUtils.isEmpty(value) && !value.equals(NOT_NULL_VALUE) && !value.equals(NULL_VALUE);
  }

  /**
   * Extracts all query parameters that match a NameSearchParameter and puts it into the request filters.
   */
  public void addQueryParams(MultivaluedMap<String, String> params) {
    for (Map.Entry<String, List<String>> param : params.entrySet()) {
      VocabularyUtils.lookup(param.getKey(), NameSearchParameter.class).ifPresent(p -> {
        addFilter(p, param.getValue());
      });
    }
  }

  public void addFilter(NameSearchParameter param, String value) {
    if (isLiteral(value)) {
      // make sure we can parse the string value
      param.from(value);
    }
    // Otherwise we leave it to the backend to interpret the value appropriately
    add(param, value);
  }

  public void addFilter(NameSearchParameter param, String[] values) {
    Arrays.stream(values).forEach(v -> addFilter(param, v));
  }

  public void addFilter(NameSearchParameter param, Object value) {
    Preconditions.checkArgument(value.getClass().equals(param.type()));
    add(param, value.toString());
  }

  public void addFilter(NameSearchParameter param, Object[] values) {
    Arrays.stream(values).forEach(v -> addFilter(param, v));
  }

  public void addFacet(NameSearchParameter facet) {
    facets.add(facet);
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

  public boolean isEmpty() {
    return q == null && super.isEmpty() && facets.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
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
