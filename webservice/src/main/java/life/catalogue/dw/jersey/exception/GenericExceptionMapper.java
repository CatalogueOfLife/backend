package life.catalogue.dw.jersey.exception;

import javax.ws.rs.ext.Provider;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;

/**
 * Improves the last resort LoggingExceptionMapper to leave the original exception in the error message
 * so its viewable immediately in Kibana.
 */
@Provider
public class GenericExceptionMapper extends LoggingExceptionMapper<Exception> {

  @Override
  protected String formatLogMessage(long id, Throwable e) {
    return String.format("%s: Error handling a request: %016x", e.getClass().getSimpleName(), id);
  }
}
