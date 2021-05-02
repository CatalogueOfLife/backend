package life.catalogue.dw.jersey.exception;

import life.catalogue.es.InvalidQueryException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a InvalidQueryException into a http 400 bad request.
 */
@Provider
public class InvalidQueryExceptionMapper extends JsonExceptionMapperBase<InvalidQueryException> {
  
  public InvalidQueryExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
