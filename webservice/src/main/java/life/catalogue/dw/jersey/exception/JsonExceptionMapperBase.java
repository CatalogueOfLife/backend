package life.catalogue.dw.jersey.exception;


import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

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
  
  public JsonExceptionMapperBase(Response.StatusType errorCode) {
    this(errorCode, false, true);
  }
  
  public JsonExceptionMapperBase(Response.StatusType errorCode, boolean debug, boolean stacktrace) {
    this.errorCode = errorCode;
    this.debug = debug;
    this.stacktrace = stacktrace;
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
    return toResponse(null, ex);
  }

  protected Response toResponse(String message, T ex) {
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

    if (message == null) {
      return jsonErrorResponse(errorCode, message(ex));
    }
    return jsonErrorResponse(errorCode, message, message(ex));
  }
  
  String message(T e) {
    return e.getMessage();
  }

}
