package org.col.jersey.exception;

import org.col.db.NotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a NotFoundException into a 404.
 */
@Provider
public class NotFoundExceptionMapper extends JsonExceptionMapperBase<NotFoundException> {

  public NotFoundExceptionMapper() {
    super(Response.Status.NOT_FOUND);
  }
}
