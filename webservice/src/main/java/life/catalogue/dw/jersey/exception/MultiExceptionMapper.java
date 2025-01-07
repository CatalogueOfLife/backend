package life.catalogue.dw.jersey.exception;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import life.catalogue.img.UnsupportedFormatException;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.jersey.server.ParamException;

import static life.catalogue.dw.jersey.exception.JsonExceptionMapperBase.jsonErrorResponse;

/**
 * Converts a {@link MultiException} into a 400 bad request if the underlying cause maps.
 * Otherwise falls back to a default 500 server error.
 */
@Provider
public class MultiExceptionMapper implements ExceptionMapper<MultiException> {

  @Override
  public Response toResponse(MultiException ex) {
    if (ex.getCause() instanceof IllegalArgumentException
      || ex.getCause() instanceof UnsupportedFormatException
      || ex.getCause() instanceof BadRequestException
      || ex.getCause() instanceof ParamException.QueryParamException
    ) {
      return jsonErrorResponse(Response.Status.BAD_REQUEST, ex.getCause().getMessage());
    }
    // map other exceptions ???
    return jsonErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, ex.getMessage());
  }
}

