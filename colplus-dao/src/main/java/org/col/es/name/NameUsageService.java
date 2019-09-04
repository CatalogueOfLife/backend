package org.col.es.name;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import org.col.es.EsException;
import org.col.es.EsModule;
import org.col.es.EsUtil;
import org.col.es.dsl.EsSearchRequest;
import org.col.es.model.NameUsageDocument;
import org.col.es.name.search.NameUsageSearchService;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;

/**
 * Base class of both the search and the suggest service, geared towards retrieving and returning raw documents. It is
 * used internally by the indexing service.
 */
public class NameUsageService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  protected final String index;
  protected final RestClient client;

  public NameUsageService(String indexName, RestClient client) {
    this.index = indexName;
    this.client = client;
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query, with payloads still pruned and zipped. Useful
   * and fast if you're only interested in the indexed fields. Since this method is currently only used internally, you
   * can (and must) compose the EsSearchRequest directly.
   * 
   * @param esSearchRequest
   * @return
   */
  public List<NameUsageDocument> getDocuments(EsSearchRequest esSearchRequest) {
    try {
      return getDocuments(index, esSearchRequest);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  /**
   * Returns the raw Elasticsearch documents matching the specified query, with payloads still pruned and zipped, and with
   * Elasticsearch's internal document ID set on the EsNameUsage instances.
   * 
   * @param esSearchRequest
   * @return
   */
  public List<NameUsageDocument> getDocumentsWithDocId(EsSearchRequest esSearchRequest) {
    try {
      return getDocumentsWithDocId(index, esSearchRequest);
    } catch (IOException e) {
      throw new EsException(e);
    }
  }

  @VisibleForTesting
  List<NameUsageDocument> getDocuments(String index, EsSearchRequest esSearchRequest) throws IOException {
    NameUsageResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameUsageResponseConverter transfer = new NameUsageResponseConverter(esResponse);
    return transfer.getDocuments();
  }

  @VisibleForTesting
  List<NameUsageDocument> getDocumentsWithDocId(String index, EsSearchRequest esSearchRequest) throws IOException {
    NameUsageResponse esResponse = executeSearchRequest(index, esSearchRequest);
    NameUsageResponseConverter transfer = new NameUsageResponseConverter(esResponse);
    return transfer.getDocumentsWithDocId();
  }

  protected NameUsageResponse executeSearchRequest(String index, EsSearchRequest esSearchRequest) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Executing query: {}", writeQuery(esSearchRequest, true));
    }
    String endpoint = String.format("/%s/%s/_search", index, DEFAULT_TYPE_NAME);
    Request httpRequest = new Request("GET", endpoint);
    httpRequest.setJsonEntity(writeQuery(esSearchRequest, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    NameUsageResponseReader reader = new NameUsageResponseReader(httpResponse);
    return reader.readResponse();
  }

  protected NameUsageMultiResponse executeMultiSearchRequest(String index, EsSearchRequest... esSearchRequests) throws IOException {
    StringBuilder sb = new StringBuilder(128);
    for (int i = 0; i < esSearchRequests.length; ++i) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Multi search query - part {}: {}", i + 1, writeQuery(esSearchRequests[i], true));
      }
      sb.append(writeQuery(esSearchRequests[i], false));
    }
    String endpoint = String.format("/%s/%s/_msearch", index, DEFAULT_TYPE_NAME);
    Request httpRequest = new Request("GET", endpoint);
    httpRequest.setJsonEntity(sb.toString());
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    NameUsageResponseReader reader = new NameUsageResponseReader(httpResponse);
    return reader.readMultiResponse();
  }

  private static String writeQuery(EsSearchRequest esSearchRequest, boolean pretty) throws JsonProcessingException {
    ObjectWriter ow = EsModule.QUERY_WRITER;
    if (pretty) {
      ow = ow.withDefaultPrettyPrinter();
    }
    return ow.writeValueAsString(esSearchRequest);
  }

}
