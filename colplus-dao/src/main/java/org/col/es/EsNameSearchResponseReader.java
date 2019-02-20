package org.col.es;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.col.es.response.EsNameSearchResponse;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts an EsNameSearchResponse from the HTTP response. This is the data structure coming back from Elasticsearch. It is passed on to a
 * NameSearchResponseTransfer object which converts it to an API object (NameSearchResponse).
 */
class EsNameSearchResponseReader {

  private static final Logger LOG = LoggerFactory.getLogger(EsNameSearchResponseReader.class);

  private static final ObjectReader RESPONSE_READER;
  private static final ObjectWriter RESPONSE_WRITER; // Only used in TRACE mode

  static {
    RESPONSE_READER = EsModule.MAPPER.readerFor(EsNameSearchResponse.class);
    RESPONSE_WRITER = EsModule.MAPPER.writerFor(EsNameSearchResponse.class).withDefaultPrettyPrinter();
  }

  private final Response httpResponse;

  EsNameSearchResponseReader(Response httpResponse) {
    this.httpResponse = httpResponse;
  }

  EsNameSearchResponse readHttpResponse() throws IOException {
    EsNameSearchResponse response = RESPONSE_READER.readValue(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      LOG.trace("Receiving response: {}", RESPONSE_WRITER.writeValueAsString(response));
    }
    return response;
  }

}
