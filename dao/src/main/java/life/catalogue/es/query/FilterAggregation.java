package life.catalogue.es.query;

import life.catalogue.es.nu.search.FacetsTranslator;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Models the Elastic search filter aggregation type. This type of aggregation is used in two ways:
 * <ol>
 * <li>It functions a (really just) a query limiting the document set over which <i>all</i> individual facets aggregate. See also
 * {@link GlobalAggregation}.
 * <li>It is used by the individual facets to retrieve the values ("SQL groups") within each facet. See also {@link FacetsTranslator} and
 * {@link FacetAggregation}.
 * </ol>
 *
 */
@JsonPropertyOrder({"filter", "aggs"})
public class FilterAggregation extends BucketAggregation {

  final Query filter;

  public FilterAggregation(Query filter) {
    this.filter = filter;
  }

}
