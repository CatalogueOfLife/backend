package org.col.es;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameUsageWrapper;
import org.col.es.model.EsNameUsage;
import org.col.es.query.EsSearchRequest;
import org.col.es.translate.NameSearchRequestTranslator;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.util.CollectionUtils.isEmpty;
import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.NAME_USAGE_BASE;;

public class NameUsageSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private final RestClient client;
  private final ObjectReader responseReader;
  private final ObjectWriter responseWriter;
  private final SearchResponseTransfer transfer;

  public NameUsageSearchService(RestClient client) {
    this.client = client;
    this.responseReader = getResponseReader();
    this.responseWriter = getResponseWriter();
    this.transfer = new SearchResponseTransfer();
  }

  public ResultPage<NameUsageWrapper<NameUsage>> search(NameSearchRequest query, Page page) throws InvalidQueryException {
    return search(NAME_USAGE_BASE, query, page);
  }

  @VisibleForTesting
  ResultPage<NameUsageWrapper<NameUsage>> search(String index, NameSearchRequest query, Page page) throws InvalidQueryException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    if (isEmpty(query.getFacets())) {
      EsSearchRequest esQuery = translator.translate();
      return search(index, esQuery, page);
    }
    return null;
  }

  @VisibleForTesting
  ResultPage<NameUsageWrapper<NameUsage>> search(String index, EsSearchRequest esQuery, Page page) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Executing query: " + writeQuery(esQuery, true));
    }
    Request httpRequest = new Request("GET", getUrl(index));
    httpRequest.setJsonEntity(writeQuery(esQuery, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    EsSearchResponse<EsNameUsage> response = readResponse(httpResponse);
    int total = response.getHits().getTotal();
    List<NameUsageWrapper<NameUsage>> nus = transfer.transfer(response);
    return new ResultPage<>(page, total, nus);
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

  private EsSearchResponse<EsNameUsage> readResponse(Response response) {
    try {
      EsSearchResponse<EsNameUsage> r = responseReader.readValue(response.getEntity().getContent());
      if (LOG.isDebugEnabled()) {
        LOG.debug("Receiving Response: " + responseWriter.writeValueAsString(r));
      }
      return r;
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  private static ObjectReader getResponseReader() {
    return EsModule.MAPPER.readerFor(new TypeReference<EsSearchResponse<EsNameUsage>>() {});
  }

  private static ObjectWriter getResponseWriter() {
    return EsModule.MAPPER.writerFor(new TypeReference<EsSearchResponse<EsNameUsage>>() {}).withDefaultPrettyPrinter();
  }

  private static String getUrl(String indexName) {
    return String.format("/%s/%s/_search", indexName, DEFAULT_TYPE_NAME);
  }
}
