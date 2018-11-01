package org.col.es;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

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

import static org.col.es.EsConfig.DEFAULT_TYPE_NAME;
import static org.col.es.EsConfig.NAME_USAGE_BASE;

public class NameUsageSearchService {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageSearchService.class);

  private static String REQUEST_URL = getUrl();

  private final RestClient client;
  private final EsConfig cfg;

  private final ObjectReader responseReader;
  private final SearchResponseTransfer transfer;

  public NameUsageSearchService(RestClient client, EsConfig cfg) {
    this.client = client;
    this.cfg = cfg;
    this.responseReader = getResponseReader();
    this.transfer = new SearchResponseTransfer(getPayloadReader());
  }

  public ResultPage<NameUsageWrapper<? extends NameUsage>> search(NameSearchRequest query,
      Page page) throws InvalidQueryException {
    NameSearchRequestTranslator translator = new NameSearchRequestTranslator(query, page);
    EsSearchRequest esQuery = translator.translate();
    if (LOG.isDebugEnabled()) {
      LOG.debug(writeQuery(esQuery, true));
    }
    Request httpRequest = new Request("GET", REQUEST_URL);
    httpRequest.setJsonEntity(writeQuery(esQuery, false));
    Response httpResponse = EsUtil.executeRequest(client, httpRequest);
    SearchResponse<EsNameUsage> response = readResponse(httpResponse);
    int total = response.getHits().getTotal();
    List<NameUsageWrapper<? extends NameUsage>> nus = transfer.transfer(response);
    return new ResultPage<>(page, total, nus);
  }

  private String writeQuery(EsSearchRequest query, boolean pretty) {
    ObjectWriter ow = cfg.nameUsage.getQueryWriter();
    if (pretty) {
      ow = ow.withDefaultPrettyPrinter();
    }
    try {
      return ow.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  private SearchResponse<EsNameUsage> readResponse(Response response) {
    try {
      return responseReader.readValue(response.getEntity().getContent());
    } catch (UnsupportedOperationException | IOException e) {
      throw new EsException(e);
    }
  }

  private ObjectReader getResponseReader() {
    return cfg.nameUsage.getMapper().readerFor(new TypeReference<SearchResponse<EsNameUsage>>() {});
  }

  private ObjectReader getPayloadReader() {
    return cfg.nameUsage.getMapper().readerFor(EsUtil.NUW_TYPE_REF);
  }

  private static String getUrl() {
    return String.format("/%s/%s", NAME_USAGE_BASE, DEFAULT_TYPE_NAME);
  }
}
