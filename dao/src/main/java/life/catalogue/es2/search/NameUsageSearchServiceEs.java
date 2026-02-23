package life.catalogue.es2.search;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es2.EsException;
import life.catalogue.es2.EsQueryService;

import java.io.IOException;

import life.catalogue.es2.query.RequestValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;

public class NameUsageSearchServiceEs extends EsQueryService implements NameUsageSearchService {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSearchServiceEs(String indexName, ElasticsearchClient client) {
    super(indexName, client);
  }

  public NameUsageSearchResponse search(NameUsageSearchRequest request, Page page) {
    try {
      new RequestValidator(request).validateRequest();
      RequestTranslator translator = new RequestTranslator(request, page);
      SearchRequest esSearchRequest = translator.translateRequest(index);
      SearchResponse<NameUsageWrapper> esResponse = client.search(esSearchRequest, NameUsageWrapper.class);
      ResponseConverter converter = new ResponseConverter(esResponse);
      return converter.convertEsResponse(page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

}
