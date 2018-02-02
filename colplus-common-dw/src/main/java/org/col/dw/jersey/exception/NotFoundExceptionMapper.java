package org.col.dw.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import org.col.db.NotFoundException;

/**
 * Converts a {@link NotFoundException} into a 404.
 */
@Provider
public class NotFoundExceptionMapper extends JsonExceptionMapperBase<NotFoundException> {

  public NotFoundExceptionMapper() {
    super(Response.Status.NOT_FOUND);
  }
}
