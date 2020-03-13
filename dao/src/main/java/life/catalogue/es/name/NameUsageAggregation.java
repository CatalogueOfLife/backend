package life.catalogue.es.name;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.EsModule;
import life.catalogue.es.response.Aggregation;
import life.catalogue.es.response.EsFacet;

public class NameUsageAggregation extends Aggregation {

  public EsFacet getFacet(NameUsageSearchParameter param) {
    Object val = get(param.getFacetLabel());
    if (val == null) {
      return null;
    }
    return EsModule.convertValue(get(param.getFacetLabel()), EsFacet.class);
  }

}
