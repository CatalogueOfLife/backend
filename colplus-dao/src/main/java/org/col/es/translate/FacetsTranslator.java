package org.col.es.translate;

import java.util.Map;

import org.col.es.query.Aggregation;

public interface FacetsTranslator {

  Map<String, Aggregation> translate();
  

}
