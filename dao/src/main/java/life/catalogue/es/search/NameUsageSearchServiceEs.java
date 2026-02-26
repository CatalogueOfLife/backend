package life.catalogue.es.search;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsException;
import life.catalogue.es.EsQueryService;

import java.io.IOException;

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
      new SearchRequestValidator(request).validateRequest();
      SearchRequestTranslator translator = new SearchRequestTranslator(request, page);
      SearchRequest esSearchRequest = translator.translateRequest(index);
      SearchResponse<NameUsageWrapper> esResponse = client.search(esSearchRequest, NameUsageWrapper.class);
      SearchResponseConverter converter = new SearchResponseConverter(esResponse);
      return converter.convertEsResponse(page);

    } catch (ElasticsearchException e) {
      LOG.error("Elasticsearch error: {} => {}", e.getMessage(), e.response().error());
      throw new EsException(e);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

}
