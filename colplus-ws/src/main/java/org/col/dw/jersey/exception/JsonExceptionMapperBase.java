package org.col.dw.jersey.exception;


import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to write exception mappers that return json error messages
 * with fixed http codes.
 */
public class JsonExceptionMapperBase<T extends Throwable> implements ExceptionMapper<T> {
  
  private static final Logger LOG = LoggerFactory.getLogger(JsonExceptionMapperBase.class);
  private final Response.StatusType errorCode;
  
  public JsonExceptionMapperBase(Response.StatusType errorCode) {
    this.errorCode = errorCode;
  }
  
  public static Response.ResponseBuilder jsonErrorResponseBuilder(Response.StatusType errorCode, String message) {
    return Response
        .status(errorCode)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new ErrorMessage(errorCode.getStatusCode(), message));
  }
  
  static Response jsonErrorResponse(Response.StatusType errorCode, String message) {
    return jsonErrorResponseBuilder(errorCode, message).build();
  }
  
  @Override
  public Response toResponse(T exception) {
    LOG.info(exception.getMessage(), exception);
    return jsonErrorResponse(errorCode, message(exception));
  }
  
  String message(T e) {
    return e.getMessage();
  }
  
}
