package org.col.es.translate;

import java.util.Map;

import org.col.es.query.Aggregation;

interface FacetsTranslator {

  Map<String, Aggregation> translate();

  default String getFacetLabel(String field) {
    return field.toUpperCase() + "__FACET";
  }

}
