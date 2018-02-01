package org.col.dw.jersey.exception;

import org.glassfish.jersey.server.ParamException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Maps query parameter exceptions to 400 bad requests instead of the JAX-RS standard 404.
 * Note that we other parameter exceptions like PathParams as 404s.
 */
@Provider
public class QueryParam400Mapper extends JsonExceptionMapperBase<ParamException.QueryParamException> {

  public QueryParam400Mapper() {
    super(Response.Status.BAD_REQUEST);
  }

  @Override
  String message(ParamException.QueryParamException ex) {
    StringBuilder msg = new StringBuilder();
    msg.append("Bad query parameter ");
    msg.append(ex.getParameterName());
    msg.append(". ");
    msg.append(getInitialCause(ex.getCause()));
    return msg.toString();
  }

  private String getInitialCause(Throwable e) {
    if (e.getCause() != null) {
      return getInitialCause(e.getCause());
    }
    return e.getLocalizedMessage();
  }
}