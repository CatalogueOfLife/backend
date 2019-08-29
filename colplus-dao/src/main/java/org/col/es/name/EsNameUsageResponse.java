package org.col.es.name;

import org.col.es.model.NameUsageDocument;
import org.col.es.response.EsAggregationQueryResponse;

/**
 * A subclass of EsSearchResponse specific for queries against the name usage index. Used by the the name search service
 * and the name suggestion service.
 */
public class EsNameUsageResponse extends EsAggregationQueryResponse<NameUsageDocument, EsNameFacetsContainer> {

}
