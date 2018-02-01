package org.col.dw.jersey.exception;


import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Base class to write exception mappers that return json error messages.
 */
public class JsonExceptionMapperBase<T extends Throwable> implements ExceptionMapper<T> {

  private static final Logger LOG = LoggerFactory.getLogger(JsonExceptionMapperBase.class);
  private final Response.StatusType errorCode;

  public JsonExceptionMapperBase(Response.StatusType errorCode) {
    this.errorCode = errorCode;
  }

  @Override
  public Response toResponse(T exception) {
    LOG.info(exception.getMessage(), exception);
    return Response
        .status(errorCode)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new ErrorMessage(errorCode.getStatusCode(), message(exception)))
        .build();
  }

  String message(T e) {
    return e.getMessage();
  }

}
