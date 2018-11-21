package org.col.es.translate;

import com.google.common.base.Preconditions;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.es.InvalidQueryException;
import org.col.es.query.BoolQuery;
import org.col.es.query.ConstantScoreQuery;
import org.col.es.query.EsSearchRequest;
import org.col.es.query.Query;

import static org.col.common.util.CollectionUtils.*;

/**
 * Translates a CoL NameSearchRequest into an actual Elasticsearch query. Main class of this package.
 *
 */
public class NameSearchRequestTranslator {

  static Query generateQuery(NameSearchRequest request) throws InvalidQueryException {
    Query query1 = new FiltersTranslator(request).translate();
    Query query2 = new QTranslator(request).translate();
    Query query = null;
    if (query1 != null) {
      if (query2 != null) {
        query = new BoolQuery().filter(query1).filter(query2);
      } else {
        query = query1;
      }
    } else if (query2 != null) {
      query = query2;
    }
    if (query != null) {
      query = new ConstantScoreQuery(query);
    }
    return query;
  }

  private final NameSearchRequest request;
  private final Page page;

  public NameSearchRequestTranslator(NameSearchRequest request, Page page) {
    this.request = request;
    this.page = page;
  }

  public EsSearchRequest generateMainQuery() throws InvalidQueryException {
    EsSearchRequest es = new EsSearchRequest();
    es.setFrom(page.getOffset());
    es.setSize(page.getLimit());
    es.setQuery(generateQuery(request));
    es.setSort(new SortByTranslator(request).translate());
    return es;
  }

  public EsSearchRequest generateFacetQuery() throws InvalidQueryException {
    Preconditions.checkArgument(notEmpty(request.getFacets()), "Should check for presence of facets first");
    /*
     * Create two copies of the NameSearchRequest object. In the first copy we get rid of all filters that are also facets. The remaining
     * filters (plus the q parameter) will produce a document set that each of the facets will aggregate over. Each facet will apply
     * additional filters further narrowing the document set, but these filters are facet-specific. In the second copy we only retain
     * filters that are also facets. That one we hand over to the facets translator.
     */
    NameSearchRequest copy1 = request.copy();
    NameSearchRequest copy2 = request.copy();
    if (notEmpty(copy1.getFilters())) {
      copy1.getFilters().keySet().removeAll(request.getFacets());
      copy2.getFilters().keySet().retainAll(request.getFacets());
    }
    EsSearchRequest es = new EsSearchRequest();
    // Not interested in the documents themselves here
    es.setSize(0);
    es.setQuery(generateQuery(copy1));
    es.setAggregations(new FacetsTranslator(copy2).translate());
    return es;
  }

}
