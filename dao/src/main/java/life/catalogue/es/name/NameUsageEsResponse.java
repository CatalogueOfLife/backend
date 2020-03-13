package life.catalogue.es.name;

import life.catalogue.es.model.NameUsageDocument;
import life.catalogue.es.response.EsResponse;

/**
 * A subclass of EsResponse narrowed to queries against the name usage index.
 */
public class NameUsageEsResponse extends EsResponse<NameUsageDocument, NameUsageAggregation> {

}
