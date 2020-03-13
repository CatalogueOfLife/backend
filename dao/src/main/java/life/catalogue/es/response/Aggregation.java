package life.catalogue.es.response;

import java.util.LinkedHashMap;

/**
 * The data structure within the ES response within which we will find the facets. This is a pretty free-style object in the Elasticsearch
 * response, so it's up to subclasses to provide more detail.
 */
public class Aggregation extends LinkedHashMap<String, Object> {

  public int getDocCount() {
    return (Integer) get("doc_count");
  }

}
