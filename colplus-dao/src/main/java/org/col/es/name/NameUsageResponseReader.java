package org.col.es.name;

import java.io.IOException;

import org.col.es.EsModule;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts an {@link EsNameSearchResponse} or an {@link NameUsageEsMultiResponse} from the HTTP response.
 */
public class NameUsageResponseReader {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResponseReader.class);

  private final Response httpResponse;

  public NameUsageResponseReader(Response httpResponse) {
    this.httpResponse = httpResponse;
  }

  public NameUsageEsResponse readResponse() throws IOException {
    NameUsageEsResponse response = EsModule.readEsResponse(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      LOG.trace("Receiving response: {}", EsModule.writeDebug(response));
    }
    return response;
  }

  public NameUsageEsMultiResponse readMultiResponse() throws IOException {
    NameUsageEsMultiResponse response = EsModule.readEsMultiResponse(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      LOG.trace("Receiving response: {}", EsModule.writeDebug(response));
    }
    return response;
  }
}
