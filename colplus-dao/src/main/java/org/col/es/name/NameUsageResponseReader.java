package org.col.es.name;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.apache.commons.io.IOUtils;
import org.col.es.EsModule;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts an {@link EsNameSearchResponse} or an {@link NameUsageMultiResponse} from the HTTP response.
 */
public class NameUsageResponseReader {

  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResponseReader.class);

  private static final ObjectReader RESPONSE_READER;
  private static final ObjectReader MULTI_RESPONSE_READER;

  static {
    RESPONSE_READER = EsModule.MAPPER.readerFor(NameUsageResponse.class);
    MULTI_RESPONSE_READER = EsModule.MAPPER.readerFor(NameUsageMultiResponse.class);
  }

  private final Response httpResponse;

  public NameUsageResponseReader(Response httpResponse) {
    this.httpResponse = httpResponse;
  }

  public NameUsageResponse readResponse() throws IOException {
    //LOG.info("XXXXXXXXXXXXXXX: " + IOUtils.toString(httpResponse.getEntity().getContent(),"UTF-8"));
    NameUsageResponse response = RESPONSE_READER.readValue(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      ObjectWriter ow = EsModule.MAPPER.writerFor(NameUsageResponse.class).withDefaultPrettyPrinter();
      LOG.trace("Receiving response: {}", ow.writeValueAsString(response));
    }
    return response;
  }

  public NameUsageMultiResponse readMultiResponse() throws IOException {
    NameUsageMultiResponse response = MULTI_RESPONSE_READER.readValue(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      ObjectWriter ow = EsModule.MAPPER.writerFor(NameUsageMultiResponse.class).withDefaultPrettyPrinter();
      LOG.trace("Receiving response: {}", ow.writeValueAsString(response));
    }
    return response;
  }
}
