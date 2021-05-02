package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.NotFoundException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a {@link NotFoundException} into a 404.
 */
@Provider
public class NotFoundExceptionMapper extends JsonExceptionMapperBase<NotFoundException> {
  
  public NotFoundExceptionMapper() {
    super(Response.Status.NOT_FOUND, true, false);
  }
}
