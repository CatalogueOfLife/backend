package org.col.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a IllegalArgumentException into a http 400 bad request.
 */
@Provider
public class IllegalArgumentExceptionMapper extends JsonExceptionMapperBase<IllegalArgumentException> {

  public IllegalArgumentExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
