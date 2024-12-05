package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.TooManyRequestsException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a TooManyRequestsException into a http 429 too many requests.
 */
@Provider
public class TooManyRequestsExceptionMapper extends JsonExceptionMapperBase<TooManyRequestsException> {

  public TooManyRequestsExceptionMapper() {
    super(Response.Status.TOO_MANY_REQUESTS, false, false, null);
  }
}
