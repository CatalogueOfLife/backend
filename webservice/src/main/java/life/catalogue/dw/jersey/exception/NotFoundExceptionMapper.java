package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.NotFoundException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a {@link NotFoundException} into a 404.
 */
@Provider
public class NotFoundExceptionMapper extends JsonExceptionMapperBase<NotFoundException> {
  private static final Logger LOG = LoggerFactory.getLogger(NotFoundExceptionMapper.class);

  public NotFoundExceptionMapper() {
    super(Response.Status.NOT_FOUND, true, false, null);
  }

}
