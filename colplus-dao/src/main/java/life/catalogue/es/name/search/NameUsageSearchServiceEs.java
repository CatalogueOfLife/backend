package life.catalogue.es.name.search;

import java.io.IOException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.EsException;
import life.catalogue.es.name.NameUsageEsResponse;
import life.catalogue.es.name.NameUsageQueryService;
import life.catalogue.es.query.EsSearchRequest;

public class NameUsageSearchServiceEs extends NameUsageQueryService implements NameUsageSearchService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSearchServiceEs(String indexName, RestClient client) {
    super(indexName, client);
  }

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   * 
   * @param request
   * @param page
   * @return
   */
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
    RequestTranslator translator = new RequestTranslator(request, page);
    EsSearchRequest esSearchRequest = translator.translateRequest();
    NameUsageSearchResponse response = search(index, esSearchRequest, page);
    ResponseProcessor processor = new ResponseProcessor(request, response);
    return processor.processResponse();
  }

  @VisibleForTesting
  public NameUsageSearchResponse search(String index, EsSearchRequest esSearchRequest, Page page) throws IOException {
    NameUsageEsResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameUsageSearchResponseFactory converter = new NameUsageSearchResponseFactory(esResponse);
    return converter.convertEsResponse(page);
  }

}
