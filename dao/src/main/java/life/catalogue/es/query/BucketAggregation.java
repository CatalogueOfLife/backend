package life.catalogue.es.query;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents what Elasticsearch calls a bucketing aggregation. See
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html
 */
public abstract class BucketAggregation extends AbstractAggregation {

  private Map<String, Aggregation> aggs;

  public void nest(String label, Aggregation agg) {
    if (aggs == null) {
      aggs = new LinkedHashMap<>();
    }
    aggs.put(label, agg);
  }

  public void setNestedAggregations(Map<String, Aggregation> aggs) {
    this.aggs = aggs;
  }

}
