package org.col.es.name;

import org.col.es.model.NameUsageDocument;
import org.col.es.response.EsResponse;

/**
 * A subclass of EsSearchResponse narrowed to queries against the name usage index. Used by the the name search service
 * and the name suggestion service.
 */
public class NameUsageResponse extends EsResponse<NameUsageDocument, NameUsageAggregation> {

}
