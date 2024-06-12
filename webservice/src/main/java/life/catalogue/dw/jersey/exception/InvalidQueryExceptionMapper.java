package life.catalogue.dw.jersey.exception;

import life.catalogue.es.InvalidQueryException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a InvalidQueryException into a http 400 bad request.
 */
@Provider
public class InvalidQueryExceptionMapper extends JsonExceptionMapperBase<InvalidQueryException> {
  
  public InvalidQueryExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
