package org.col.es.name;

import org.col.es.model.NameUsageDocument;
import org.col.es.response.EsResponse;

/**
 * A subclass of EsResponse narrowed to queries against the name usage index.
 */
public class NameUsageResponse extends EsResponse<NameUsageDocument, NameUsageAggregation> {

}
