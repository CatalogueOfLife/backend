package org.col.es.name.search;

import org.col.es.name.NameUsageDocument;
import org.col.es.response.EsSearchResponse;

/**
 * A subclass of EsSearchResponse specific for name searches.
 */
public class EsNameSearchResponse extends EsSearchResponse<NameUsageDocument, EsNameFacetsContainer> {
  
}
