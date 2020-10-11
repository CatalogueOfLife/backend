package life.catalogue.es.nu.search;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.EsException;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.nu.NameUsageQueryService;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.response.EsResponse;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static life.catalogue.api.search.NameUsageRequest.SearchType.EXACT;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.es.EsUtil.getSearchTerms;
import static life.catalogue.es.ddl.Analyzer.SCINAME_WHOLE_WORDS;

public class NameUsageSearchServiceEs extends NameUsageQueryService implements NameUsageSearchService {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchServiceEs.class);

  public NameUsageSearchServiceEs(String indexName, RestClient client) {
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
      request.setSciNameSearchTerms(getSearchTerms(client, index, SCINAME_WHOLE_WORDS, q));
    }
    RequestTranslator translator = new RequestTranslator(request, page);
    EsSearchRequest esSearchRequest = translator.translateRequest();
    NameUsageSearchResponse response = search(index, esSearchRequest, page);
    ResponsePostProcessor processor = new ResponsePostProcessor(request, response);
    return processor.processResponse();
  }

  @VisibleForTesting
  public NameUsageSearchResponse search(String index, EsSearchRequest esSearchRequest, Page page)
      throws IOException {
    EsResponse<EsNameUsage> esResponse = executeSearchRequest(index, esSearchRequest);
    ResponseConverter converter = new ResponseConverter(esResponse);
    return converter.convertEsResponse(page);
  }

}
