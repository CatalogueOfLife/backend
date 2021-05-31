package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.UnavailableException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a UnavailableException into a http 503 response.
 * IllegalStateExceptionMapper
 */
@Provider
public class UnavailableExceptionMapper extends JsonExceptionMapperBase<UnavailableException> {

  public UnavailableExceptionMapper() {
    super(Response.Status.SERVICE_UNAVAILABLE, false, false);
  }
}
