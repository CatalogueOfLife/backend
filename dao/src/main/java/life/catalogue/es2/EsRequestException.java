package life.catalogue.es2;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime exception specifically indicating an error while executing a client request or handling the response. Although this is a runtime
 * exception it could make sense to catch it nonetheless.
 */
public class EsRequestException extends EsException {

  private static final Logger LOG = LoggerFactory.getLogger(EsUtil.class);
  private static final String BASE_MESSAGE = "Elasticsearch request failure.";

  public EsRequestException(String msg, Object... msgArgs) {
    super(BASE_MESSAGE + ' ' + String.format(msg, msgArgs));
  }

  public EsRequestException(Throwable t) {
    super(generateErrorMessage(t));
    initCause(t);
    LOG.error(getMessage());
  }

  private static String generateErrorMessage(Throwable t) {
    StringBuilder sb = new StringBuilder(100);
    sb.append(BASE_MESSAGE).append(' ').append(t.getMessage());
    if (t.getCause() != null && !Objects.equals(t.getMessage(), t.getCause().getMessage())) {
      sb.append(". Caused by: ").append(t.getCause());
    }
    return sb.toString();
  }

}
