package life.catalogue.es.response;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.es.EsModule;

import java.util.LinkedHashMap;
import java.util.Map;

public class FacetsContainer extends LinkedHashMap<String, Object> {

  public FacetsContainer(Map<String, Object> parentAgg) {
    super(parentAgg);
  }

  public int getDocCount() {
    return (Integer) get("doc_count");
  }

  public EsFacet getFacet(NameUsageSearchParameter param) {
    Object val = get(param.name());
    if (val == null) {
      return null;
    }
    return EsModule.convertValue(get(param.name()), EsFacet.class);
  }

}
