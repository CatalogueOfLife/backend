package org.col.es;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

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

public class NameUsageSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private final String index;
  private final RestClient client;

  public NameUsageSearchService(String indexName, RestClient client) {
    this.index = indexName;
    this.client = client;
  }

  /**
   * Converts the Elasticsearh response coming back from the query into an API object (NameSearchResponse).
   * 
   * @param nameSearchRequest
   * @param page
   * @return
   */
  public NameSearchResponse search(NameSearchRequest nameSearchRequest, Page page) {
    try {
      return search(index, nameSearchRequest, page);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }
  
  /**
   * Returns the raw Elasticsearch documents matching the specified query, with payloads still pruned and zipped. Useful and fast if you're
   * only interested in the indexed fields. Since this method is currently only used internally, you can (and must) compose the
   * EsSearchRequest directly.
   * 
   * @param esSearchRequest
   * @return
   */
  public List<EsNameUsage> getDocuments(EsSearchRequest esSearchRequest) {
    try {
      return getDocuments(index, esSearchRequest);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query, with payloads still pruned and zipped, and with Elasticsearch's
   * internal document ID set on the EsNameUsage instances.
   * 
   * @param esSearchRequest
   * @return
   */
  public List<EsNameUsage> getDocumentsWithDocId(EsSearchRequest esSearchRequest) {
    try {
      return getDocumentsWithDocId(index, esSearchRequest);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  NameSearchResponse search(String index, NameSearchRequest colSearchRequest, Page page) throws IOException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(colSearchRequest, page);
    EsSearchRequest esSearchRequest = translator.translate();
    EsNameSearchResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    NameSearchResponse colResponse = transfer.transferResponse(page);
    if (mustHighlight(colSearchRequest, colResponse)) {
      NameSearchHighlighter highlighter = new NameSearchHighlighter(colSearchRequest, colResponse);
      highlighter.highlightNameUsages();
    }
    return colResponse;
  }

  @VisibleForTesting
  NameSearchResponse search(String index, EsSearchRequest esSearchRequest, Page page) throws IOException {
    EsNameSearchResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    return transfer.transferResponse(page);
  }

  @VisibleForTesting
  List<EsNameUsage> getDocuments(String index, EsSearchRequest esSearchRequest) throws IOException {
    EsNameSearchResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    return transfer.getDocuments();
  }

  @VisibleForTesting
  List<EsNameUsage> getDocumentsWithDocId(String index, EsSearchRequest esSearchRequest) throws IOException {
    EsNameSearchResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse);
    return transfer.getDocumentsWithDocId();
  }

  private EsNameSearchResponse executeSearchRequest(String index, EsSearchRequest esSearchRequest) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing query: {}", writeQuery(esSearchRequest, true));
    }
    Request httpRequest = new Request("GET", endpoint(index));
    httpRequest.setJsonEntity(writeQuery(esSearchRequest, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    EsNameSearchResponseReader reader = new EsNameSearchResponseReader(httpResponse);
    return reader.readHttpResponse();
  }

  private static String writeQuery(EsSearchRequest esSearchRequest, boolean pretty) throws JsonProcessingException {
    ObjectWriter ow = EsModule.QUERY_WRITER;
    if (pretty) {
      ow = ow.withDefaultPrettyPrinter();
    }
    return ow.writeValueAsString(esSearchRequest);
  }

  private static boolean mustHighlight(NameSearchRequest req, NameSearchResponse res) {
    return req.isHighlight() && !res.getResult().isEmpty() && req.getQ() != null && req.getQ().length() > 1;
  }

  private static String endpoint(String indexName) {
    return String.format("/%s/%s/_search", indexName, DEFAULT_TYPE_NAME);
  }
}
