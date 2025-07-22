package life.catalogue.api.model;

import life.catalogue.api.search.FacetValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class FacettedResultPage<T, F> extends ResultPage<T> {
  private Map<F, List<FacetValue<?>>> facets;

  public FacettedResultPage(Map<F, List<FacetValue<?>>> facets) {
    this.facets = facets;
  }

  public FacettedResultPage(Page page, int total, List<T> result, Map<F, List<FacetValue<?>>> facets) {
    super(page, total, result);
    this.facets = facets;
  }

  public FacettedResultPage(Page page, List<T> result, Supplier<Integer> count, Map<F, List<FacetValue<?>>> facets) {
    super(page, result, count);
    this.facets = facets;
  }

  public Map<F, List<FacetValue<?>>> getFacets() {
    return facets;
  }

  public void setFacets(Map<F, List<FacetValue<?>>> facets) {
    this.facets = facets;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FacettedResultPage)) return false;
    if (!super.equals(o)) return false;
    FacettedResultPage<?, ?> that = (FacettedResultPage<?, ?>) o;
    return Objects.equals(facets, that.facets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), facets);
  }
}
