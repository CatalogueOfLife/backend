package life.catalogue.es;

import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime exception specifically indicating an error while executing a client request or handling the response. Although this is a runtime
 * exception it could make sense to catch it nonetheless.
 */
public class EsRequestException extends EsException {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);
  private static final String BASE_MESSAGE = "Elasticsearch request failure.";

  private final Response response;

  public EsRequestException(String msg, Object... msgArgs) {
    super(BASE_MESSAGE + ' ' + String.format(msg, msgArgs));
    this.response = null;
  }

  public EsRequestException(Response response) {
    super(generateErrorMessage(response));
    LOG.error(getMessage());
    this.response = response;
  }

  public EsRequestException(Throwable t) {
    super(generateErrorMessage(t));
    initCause(t);
    LOG.error(getMessage());
    if (t.getClass() == ResponseException.class) {
      this.response = ((ResponseException) t).getResponse();
    } else {
      this.response = null;
    }
  }

  public Response getResponse() {
    return response;
  }

  private static String generateErrorMessage(Throwable t) {
    if (t.getClass() == ResponseException.class) {
      return generateErrorMessage(((ResponseException) t).getResponse());
    }
    StringBuilder sb = new StringBuilder(100);
    sb.append(BASE_MESSAGE).append(' ').append(t.getMessage());
    if (t.getCause() != null) {
      /*
       * See documentation for RestClient.performRequest. Just in case the throwable is a rather non-descript wrapper around the real cause,
       * we also append the cause to the error message.
       */
      sb.append(". Caused by: ").append(t.getCause());
    }
    return sb.toString();
  }

  private static String generateErrorMessage(Response response) {
    StringBuilder sb = new StringBuilder(100);
    sb.append(BASE_MESSAGE).append(' ')
        .append(response.getStatusLine().getStatusCode()).append(' ')
        .append(response.getStatusLine().getReasonPhrase()).append('.');
    if (response.getEntity() != null) {
      sb.append(' ').append(EsUtil.getErrorMessage(response));
    }
    return sb.toString();
  }

}
