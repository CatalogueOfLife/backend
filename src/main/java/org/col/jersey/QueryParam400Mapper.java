package org.col.jersey;

import io.dropwizard.jersey.errors.ErrorMessage;
import org.glassfish.jersey.server.ParamException;

import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Maps query parameter exceptions to 400 bad requests instead of the JAX-RS standard 404.
 * Note that we other parameter exceptions like PathParams as 404s.
 */
@Provider
@Singleton
public class QueryParam400Mapper implements ExceptionMapper<ParamException.QueryParamException> {

  public Response toResponse(ParamException.QueryParamException ex) {
    StringBuilder msg = new StringBuilder();
    msg.append("Bad query parameter ");
    msg.append(ex.getParameterName());
    msg.append(". ");
    msg.append(getInitialCause(ex.getCause()));
    return Response.fromResponse(ex.getResponse())
        .type(MediaType.APPLICATION_JSON_TYPE)
        .status(Response.Status.BAD_REQUEST)
        .entity(new ErrorMessage(Response.Status.BAD_REQUEST.getStatusCode(), msg.toString()))
        .build();
  }

  private String getInitialCause(Throwable e) {
    if (e.getCause() != null) {
      return getInitialCause(e.getCause());
    }
    return e.getLocalizedMessage();
  }
}