package life.catalogue.api.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;

import java.util.*;
import java.util.function.Supplier;

public class NameUsageSearchResponse extends ResultPage<NameUsageWrapper> {

  private final Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets;

  public NameUsageSearchResponse() {
    super();
    this.facets = null;
  }

  public NameUsageSearchResponse(Page page, int total, List<NameUsageWrapper> result) {
    this(page, total, result, Collections.emptyMap());
  }

  public NameUsageSearchResponse(Page page, int total, List<NameUsageWrapper> result,
      Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets) {
    super(page, total, result);
    this.facets = facets;
  }

  public NameUsageSearchResponse(Page page, List<NameUsageWrapper> result, Map<NameUsageSearchParameter, Set<FacetValue<?>>> facets,
      Supplier<Integer> count) {
    super(page, result, count);
    this.facets = facets;
  }

  public Map<NameUsageSearchParameter, Set<FacetValue<?>>> getFacets() {
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
    NameUsageSearchResponse that = (NameUsageSearchResponse) o;
    return Objects.equals(facets, that.facets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), facets);
  }
}
