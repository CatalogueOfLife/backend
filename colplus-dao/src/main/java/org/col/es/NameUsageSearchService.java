package org.col.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchResponse;
import org.col.es.query.EsSearchRequest;
import org.col.es.response.EsNameSearchResponse;
import org.col.es.translate.NameSearchRequestTranslator;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.NAME_USAGE_BASE;;

public class NameUsageSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private final RestClient client;

  public NameUsageSearchService(RestClient client) {
    this.client = client;
  }

  public NameSearchResponse search(NameSearchRequest query, Page page) {
    return search(NAME_USAGE_BASE, query, page);
  }

  @VisibleForTesting
  NameSearchResponse search(String index, NameSearchRequest query, Page page) {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    EsSearchRequest esSearchRequest = translator.translate();
    return search(index, esSearchRequest, page);
  }

  @VisibleForTesting
  NameSearchResponse search(String index, EsSearchRequest esQuery, Page page) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing query: " + writeQuery(esQuery, true));
    }
    Request httpRequest = new Request("GET", getUrl(index));
    httpRequest.setJsonEntity(writeQuery(esQuery, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    EsNameSearchResponseReader reader = new EsNameSearchResponseReader(httpResponse);
    EsNameSearchResponse esResponse = reader.readHttpResponse();
    NameSearchResponseTransfer transfer = new NameSearchResponseTransfer(esResponse, page);
    return transfer.transferResponse();
  }

  private static String writeQuery(EsSearchRequest query, boolean pretty) {
    ObjectWriter ow = EsModule.QUERY_WRITER;
    if (pretty) {
      ow = ow.withDefaultPrettyPrinter();
    }
    try {
      return ow.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  private static String getUrl(String indexName) {
    return String.format("/%s/%s/_search", indexName, DEFAULT_TYPE_NAME);
  }
}
