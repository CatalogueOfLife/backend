package life.catalogue.es.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.EsException;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageQueryService;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import com.google.common.annotations.VisibleForTesting;

import static life.catalogue.api.search.NameUsageRequest.SearchType.EXACT;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.es.EsUtil.getSearchTerms;

public class NameUsageSearchServiceEs extends NameUsageQueryService implements NameUsageSearchService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSearchServiceEs(String indexName, ElasticsearchClient client) {
    super(indexName, client);
  }

  public NameUsageSearchResponse search(NameUsageSearchRequest request, Page page) {
    try {
      return search(index, request, page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  public NameUsageSearchResponse search(String index, NameUsageSearchRequest request, Page page) throws IOException {
    RequestValidator validator = new RequestValidator(request);
    validator.validateRequest();
    if (request.hasQ() && request.getContent().contains(SCIENTIFIC_NAME) && request.getSearchType() != EXACT) {
      String q = request.getQ().toLowerCase();
      request.setQ(q);
      request.setSciNameSearchTerms(getSearchTerms(client, index, "sciname_whole_words", q));
    }
    RequestTranslator translator = new RequestTranslator(request, page);
    SearchRequest esSearchRequest = translator.translateRequest(index);
    SearchResponse<EsNameUsage> esResponse = executeSearchRequest(index, esSearchRequest);
    ResponseConverter converter = new ResponseConverter(esResponse);
    NameUsageSearchResponse response = converter.convertEsResponse(page);
    ResponsePostProcessor processor = new ResponsePostProcessor(request, response);
    return processor.processResponse();
  }

}
