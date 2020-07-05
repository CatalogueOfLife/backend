package life.catalogue.dw.jersey.exception;

import life.catalogue.matching.UnavailableException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a BadRequestException into a http 400 bad request.
 */
@Provider
public class UnavailableExceptionMapper extends JsonExceptionMapperBase<UnavailableException> {

  public UnavailableExceptionMapper() {
    super(Response.Status.SERVICE_UNAVAILABLE, false, false);
  }
}
