package life.catalogue.es.nu;

import life.catalogue.es.EsModule;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.UpwardConverter;
import life.catalogue.es.response.EsMultiResponse;
import life.catalogue.es.response.EsResponse;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts an {@link EsResponse} or an {@link EsMultiResponse} from the raw HTTP response. The
 * {@link EsResponse} is then passed on the {@link EsNameUsageConverter}.
 */
public class HttpResponseConverter implements UpwardConverter<Response, EsResponse<EsNameUsage>> {

  private static final Logger LOG = LoggerFactory.getLogger(HttpResponseConverter.class);

  public static EsResponse<EsNameUsage> readResponse(InputStream is) throws IOException {
    EsResponse<EsNameUsage> response = EsModule.readEsResponse(is);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Receiving response: {}", EsModule.writeDebug(response));
    }
    return response;
  }

  private Response httpResponse;

  public HttpResponseConverter(Response httpResponse) {
    this.httpResponse = httpResponse;
  }

  public EsResponse<EsNameUsage> readResponse() throws IOException {
    return readResponse(httpResponse.getEntity().getContent());
  }

  public EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>> readMultiResponse() throws IOException {
    EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>> response = EsModule.readEsMultiResponse(httpResponse.getEntity().getContent());
    if (LOG.isTraceEnabled()) {
      LOG.trace("Receiving response: {}", EsModule.writeDebug(response));
    }
    return response;
  }
}
