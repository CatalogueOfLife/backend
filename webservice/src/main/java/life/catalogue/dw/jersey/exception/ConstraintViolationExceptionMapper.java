package life.catalogue.dw.jersey.exception;

import javax.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a BadRequestException into a http 400 bad request.
 */
@Provider
public class ConstraintViolationExceptionMapper extends JsonExceptionMapperBase<ConstraintViolationException> {

  public ConstraintViolationExceptionMapper() {
    super(Response.Status.BAD_REQUEST, false, false, null);
  }
}
