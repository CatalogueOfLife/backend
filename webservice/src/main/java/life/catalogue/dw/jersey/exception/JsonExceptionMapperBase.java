package life.catalogue.dw.jersey.exception;


import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.jersey.errors.ErrorMessage;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Base class to write exception mappers that return json error messages
 * with fixed http codes.
 */
public class JsonExceptionMapperBase<T extends Throwable> implements ExceptionMapper<T> {
  
  private static final Logger LOG = LoggerFactory.getLogger(JsonExceptionMapperBase.class);
  private final Response.StatusType errorCode;
  private final boolean debug;
  private final boolean stacktrace;
  private final String defaultMessage;

  public JsonExceptionMapperBase(Response.StatusType errorCode) {
    this(errorCode,null);
  }

  public JsonExceptionMapperBase(Response.StatusType errorCode, String defaultMessage) {
    this(errorCode, false, true, defaultMessage);
  }

  public JsonExceptionMapperBase(Response.StatusType errorCode, boolean debug, boolean stacktrace, String defaultMessage) {
    this.errorCode = errorCode;
    this.debug = debug;
    this.stacktrace = stacktrace;
    this.defaultMessage = defaultMessage;
  }

  private static Response.ResponseBuilder jsonErrorResponseBuilder(Response.StatusType errorCode, String message, String details) {
    return Response
        .status(errorCode)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new ErrorMessage(errorCode.getStatusCode(), trimToNull(message), trimToNull(details)));
  }
  
  public static Response jsonErrorResponse(Response.StatusType errorCode, String message) {
    return jsonErrorResponse(errorCode, message, null);
  }

  public static Response jsonErrorResponse(Response.StatusType errorCode, String message, String details) {
    return jsonErrorResponseBuilder(errorCode, message, details).build();
  }

  @Override
  public Response toResponse(T ex) {
    if (debug) {
      if (stacktrace) {
        LOG.debug("{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      } else {
        LOG.debug("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
      }
    } else {
      if (stacktrace) {
        LOG.info("{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      } else {
        LOG.info("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
      }
    }

    if (defaultMessage == null) {
      return jsonErrorResponse(errorCode, message(ex));
    }
    return jsonErrorResponse(errorCode, defaultMessage, message(ex));
  }
  
  String message(T e) {
    return e.getMessage();
  }

}
