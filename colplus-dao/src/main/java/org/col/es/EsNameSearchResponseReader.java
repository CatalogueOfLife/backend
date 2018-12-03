package org.col.es;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectReader;

import org.col.es.response.EsNameSearchResponse;
import org.elasticsearch.client.Response;

/**
 * Extracts an EsNameSearchResponse from the HTTP response. This is the data structure coming back from Elasticsearch. It is passed on to a
 * NameSearchResponseTransfer object which converts it to an API object (NameSearchResponse).
 */
class EsNameSearchResponseReader {

  private static final ObjectReader reader;

  static {
    reader = EsModule.MAPPER.readerFor(EsNameSearchResponse.class);
  }

  private final Response httpResponse;

  EsNameSearchResponseReader(Response httpResponse) {
    this.httpResponse = httpResponse;
  }

  EsNameSearchResponse readHttpResponse() throws IOException {
    return reader.readValue(httpResponse.getEntity().getContent());
  }

}
