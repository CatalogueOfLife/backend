package org.col.es.translate;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
import org.col.es.query.Aggregation;
import org.col.es.query.FacetAggregation;
import org.col.es.query.FilterAggregation;
import org.col.es.query.GlobalAggregation;
import org.col.es.query.MatchAllQuery;
import org.col.es.query.Query;

import static java.util.Collections.singletonMap;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.common.util.CollectionUtils.notEmpty;
import static org.col.es.translate.AggregationLabelProvider.getContextFilterLabel;
import static org.col.es.translate.AggregationLabelProvider.getContextLabel;
import static org.col.es.translate.AggregationLabelProvider.getFacetLabel;
import static org.col.es.translate.NameSearchRequestTranslator.generateQuery;

/**
 * Translates the facet list in the NameSearchRequest object into a set of suitable aggregation. There is one edge case here: if there is
 * just one facet, with or without a corresponding filter, and with or without any filter at all. In this case the aggregation can take
 * place in the current execution context (the document set produced by the main query). However we wilfully incur some performance overhead
 * here by still making it take place within a separate execution context with exactly the same query as the main query. This allows for a
 * huge streamlining of the code because the response fro Elasticsearch will now always look the same. Besides, it's an unlikely scenario
 * (there will probably alsway be more than one facet). And also, Elasticseaech will probably cache the filter, reducing the performance
 * overhead.
 */
class FacetsTranslator {

  private final NameSearchRequest request;

  public FacetsTranslator(NameSearchRequest request) {
    this.request = request;
  }

  Map<String, Aggregation> translate() {
    NameSearchRequest copy = request.copy();
    if (notEmpty(request.getFilters())) {
      copy.getFilters().keySet().retainAll(request.getFacets());
    }
    copy.setQ(null);
    GlobalAggregation context = new GlobalAggregation();
    Query query = getContextFilter();
    FilterAggregation contextAggregation = new FilterAggregation(getContextFilter());
    context.setNestedAggregations(singletonMap(getContextFilterLabel(), contextAggregation));
    for (NameSearchParameter facet : copy.getFacets()) {
      String field = EsFieldLookup.INSTANCE.lookup(facet);
      NameSearchRequest tmp = copy.copy();
      tmp.removeFilter(facet);
      query = isEmpty(tmp.getFilters()) ? MatchAllQuery.INSTANCE : generateQuery(tmp);
      Aggregation agg = new FacetAggregation(field, query);
      contextAggregation.addNestedAggregation(getFacetLabel(facet), agg);
    }
    return singletonMap(getContextLabel(), context);
  }

  private Query getContextFilter() {
    NameSearchRequest copy = request.copy();
    if (notEmpty(copy.getFilters())) {
      copy.getFilters().keySet().removeAll(request.getFacets());
    }
    if (isEmpty(copy.getFilters()) && StringUtils.isEmpty(copy.getQ())) {
      return MatchAllQuery.INSTANCE;
    }
    return generateQuery(copy);
  }

}
