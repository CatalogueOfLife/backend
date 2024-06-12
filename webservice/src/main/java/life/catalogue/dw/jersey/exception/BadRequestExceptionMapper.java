package life.catalogue.dw.jersey.exception;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a BadRequestException into a http 400 bad request.
 */
@Provider
public class BadRequestExceptionMapper extends JsonExceptionMapperBase<BadRequestException> {
  
  public BadRequestExceptionMapper() {
    super(Response.Status.BAD_REQUEST, false, false, null);
  }
}
