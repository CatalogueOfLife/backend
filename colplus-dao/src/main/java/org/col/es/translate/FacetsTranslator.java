package org.col.es.translate;

import java.util.Map;

import org.col.es.query.Aggregation;

public interface FacetsTranslator {

  Map<String, Aggregation> translate();
  
  static String getFacetLabel(String field) {
    return field + "Facet";
  }
  
  static String getBucketsLabel(String field) {
    return field + "Buckets";
  }

}
