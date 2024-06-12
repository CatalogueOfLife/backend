package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.UnavailableException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a UnavailableException into a http 503 response.
 * IllegalStateExceptionMapper
 */
@Provider
public class UnavailableExceptionMapper extends JsonExceptionMapperBase<UnavailableException> {

  public UnavailableExceptionMapper() {
    super(Response.Status.SERVICE_UNAVAILABLE, false, false, null);
  }
}
