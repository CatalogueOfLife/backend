package org.col.api.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;

public class NameSearchResponse extends ResultPage<NameUsage> {
  private Map<NameSearchParameter, List<FacetCount>> facets;
  
  public NameSearchResponse(Page page, int total, List<NameUsage> result) {
    this(page, total, result, new HashMap<>());
  }
  
  public NameSearchResponse(Page page, int total, List<NameUsage> result, Map<NameSearchParameter, List<FacetCount>> facets) {
    super(page, total, result);
    this.facets = facets;
  }
  
  public Map<NameSearchParameter, List<FacetCount>> getFacets() {
    return facets;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameSearchResponse that = (NameSearchResponse) o;
    return Objects.equals(facets, that.facets);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(super.hashCode(), facets);
  }
}
