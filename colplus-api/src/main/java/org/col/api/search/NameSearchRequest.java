package org.col.api.search;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedHashMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

public class NameSearchRequest extends MultivaluedHashMap<NameSearchParameter, String> {

  public static enum SearchContent {
    SCIENTIFIC_NAME, AUTHORSHIP, VERNACULAR_NAME
  }

  public static enum SortBy {
    RELEVANCE, NAME, KEY
  }

  @QueryParam("content")
  private Set<SearchContent> content = Sets.newHashSet(SearchContent.SCIENTIFIC_NAME,
      SearchContent.AUTHORSHIP, SearchContent.VERNACULAR_NAME);

  @QueryParam("facet")
  private Set<NameSearchParameter> facets = new HashSet<>();

  @QueryParam("q")
  private String q;

  @QueryParam("sortBy")
  private SortBy sortBy = SortBy.NAME;

  public void addFilter(NameSearchParameter param, String value) {
    // make sure we can parse the string value
    param.from(value);
    add(param, value);
  }

  public void addFilter(NameSearchParameter param, Object value) {
    Preconditions.checkArgument(value.getClass().equals(param.type()));
    add(param, value.toString());
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
    return Objects.equals(content, that.content) && Objects.equals(facets, that.facets)
        && Objects.equals(q, that.q) && sortBy == that.sortBy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), content, facets, q, sortBy);
  }

}
