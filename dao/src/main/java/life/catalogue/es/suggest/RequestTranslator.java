package life.catalogue.es.suggest;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.FilterTranslator;
import life.catalogue.es.FiltersTranslator;
import life.catalogue.es.NameUsageFieldLookup;
import life.catalogue.es.SortByTranslator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;

import static life.catalogue.api.search.NameUsageSearchParameter.DATASET_KEY;
import static life.catalogue.api.search.NameUsageSearchParameter.USAGE_ID;
import static life.catalogue.api.vocab.TaxonomicStatus.ACCEPTED;
import static life.catalogue.api.vocab.TaxonomicStatus.PROVISIONALLY_ACCEPTED;

/**
 * Translates the {@code NameSuggestRequest} into a native Elasticsearch search request.
 */
class RequestTranslator {
  private static final Logger LOG = LoggerFactory.getLogger(RequestTranslator.class);

  private final NameUsageSuggestRequest request;

  RequestTranslator(NameUsageSuggestRequest request) {
    this.request = request;
  }

  SearchRequest translate(String index) {
    Query query = generateQuery(request);
    var sortOptions = new SortByTranslator(request).translate();

    return SearchRequest.of(s -> s
      .index(index)
      .query(query)
      .sort(sortOptions)
      .size(request.getLimit())
    );
  }

  static Query generateQuery(NameUsageSuggestRequest request) {
    if (request.isAccepted()) {
      request.clearFilter(NameUsageSearchParameter.STATUS);
      request.addFilter(NameUsageSearchParameter.STATUS, ACCEPTED);
      request.addFilter(NameUsageSearchParameter.STATUS, PROVISIONALLY_ACCEPTED);
    }
    if (request.isExclBareNames()) {
      if (request.hasFilter(NameUsageSearchParameter.STATUS)) {
        LOG.debug("Request already filters on taxonomic status. Ignore exclude bare names flag");
      } else {
        for (var status : TaxonomicStatus.values()) {
          if (!status.isBareName()) {
            request.addFilter(NameUsageSearchParameter.STATUS, status);
          }
        }
      }
    }

    // always avoid bare names
    final String statusField = NameUsageFieldLookup.INSTANCE.lookupSingle(NameUsageSearchParameter.STATUS);
    Query existsFilter = Query.of(q -> q.exists(e -> e.field(statusField)));
    Query snQuery = new QTranslator(request).translate();

    if (request.hasFilter(USAGE_ID)) {
      Query dkFilter = new FilterTranslator(request).translate(DATASET_KEY);
      Query idFilter = new FilterTranslator(request).translate(USAGE_ID);
      return Query.of(q -> q.bool(b -> b
        .filter(existsFilter)
        .filter(dkFilter)
        .filter(idFilter)
        .must(snQuery)
      ));
    } else if (FiltersTranslator.mustGenerateFilters(request)) {
      Query filtersQuery = new FiltersTranslator(request).translate();
      return Query.of(q -> q.bool(b -> b
        .filter(existsFilter)
        .filter(filtersQuery)
        .must(snQuery)
      ));
    }
    return Query.of(q -> q.bool(b -> b
      .filter(existsFilter)
      .must(snQuery)
    ));
  }
}
