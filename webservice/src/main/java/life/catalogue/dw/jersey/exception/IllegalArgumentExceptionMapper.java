package life.catalogue.dw.jersey.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a IllegalArgumentException into a http 400 bad request.
 */
@Provider
public class IllegalArgumentExceptionMapper extends JsonExceptionMapperBase<IllegalArgumentException> {
  
  public IllegalArgumentExceptionMapper() {
    super(Response.Status.BAD_REQUEST, false, false, null);
  }
}
