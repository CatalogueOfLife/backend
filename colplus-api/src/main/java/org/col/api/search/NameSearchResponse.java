package org.col.api.search;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;

public class NameSearchResponse extends ResultPage<NameUsageWrapper<NameUsage>> {

  private final Map<NameSearchParameter, Set<FacetValue<?>>> facets;

  public NameSearchResponse(Page page, int total, List<NameUsageWrapper<NameUsage>> result) {
    this(page, total, result, Collections.emptyMap());
  }

  public NameSearchResponse(Page page, int total, List<NameUsageWrapper<NameUsage>> result,
      Map<NameSearchParameter, Set<FacetValue<?>>> facets) {
    super(page, total, result);
    this.facets = facets;
  }

  public Map<NameSearchParameter, Set<FacetValue<?>>> getFacets() {
    return facets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    NameSearchResponse that = (NameSearchResponse) o;
    return Objects.equals(facets, that.facets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), facets);
  }
}
