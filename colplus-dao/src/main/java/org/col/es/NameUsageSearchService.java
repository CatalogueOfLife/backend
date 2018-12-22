package org.col.es;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.es.model.EsNameUsage;
import org.col.es.query.EsSearchRequest;
import org.col.es.response.EsNameSearchResponse;
import org.col.es.translate.NameSearchRequestTranslator;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.ES_INDEX_NAME_USAGE;;

public class NameUsageSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private final RestClient client;

  public NameUsageSearchService(RestClient client) {
    this.client = client;
  }

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   * 
   * @param query
   * @param page
   * @return
   */
  public NameSearchResponse search(NameSearchRequest query, Page page) {
    try {
      return search(ES_INDEX_NAME_USAGE, query, page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query (with payloads still pruned and zipped). Useful if you're only
   * interested in one (or more) of the indexed fields.
   * 
   * @param query
   * @return
   */
  public List<EsNameUsage> getDocuments(EsSearchRequest query) {
    try {
      return getDocuments(ES_INDEX_NAME_USAGE, query);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  NameSearchResponse search(String index, NameSearchRequest query, Page page) throws IOException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    EsSearchRequest esSearchRequest = translator.translate();
    return search(index, esSearchRequest, page);
  }

  NameSearchResponse search(String index, EsSearchRequest esQuery, Page page) throws IOException {
    EsNameSearchResponse esResponse = executeSearchRequest(index, esQuery);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    return transfer.transferResponse(page);
  }

  List<EsNameUsage> getDocuments(String index, EsSearchRequest query) throws IOException {
    EsNameSearchResponse esResponse = executeSearchRequest(index, query);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    return transfer.getDocuments();
  }

  private EsNameSearchResponse executeSearchRequest(String index, EsSearchRequest esQuery) throws JsonProcessingException, IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing query: " + writeQuery(esQuery, true));
    }
    Request httpRequest = new Request("GET", getUrl(index));
    httpRequest.setJsonEntity(writeQuery(esQuery, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    EsNameSearchResponseReader reader = new EsNameSearchResponseReader(httpResponse);
    EsNameSearchResponse esResponse = reader.readHttpResponse();
    return esResponse;
  }

  private static String writeQuery(EsSearchRequest query, boolean pretty) throws JsonProcessingException {
    ObjectWriter ow = EsModule.QUERY_WRITER;
    if (pretty) {
      ow = ow.withDefaultPrettyPrinter();
    }
    return ow.writeValueAsString(query);
  }

  private static String getUrl(String indexName) {
    return String.format("/%s/%s/_search", indexName, DEFAULT_TYPE_NAME);
  }
}
