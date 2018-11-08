package org.col.dw.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.col.es.InvalidQueryException;

/**
 * Converts a InvalidQueryException into a http 400 bad request.
 */
@Provider
public class InvalidQueryExceptionMapper extends JsonExceptionMapperBase<InvalidQueryException> {

  public InvalidQueryExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
