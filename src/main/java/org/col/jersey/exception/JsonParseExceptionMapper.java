package org.col.jersey.exception;

import com.fasterxml.jackson.core.JsonParseException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Implementation of {@link ExceptionMapper} to send down a "400 Bad Request"
 * in the event unparsable JSON is received.
 */
@Provider
public class JsonParseExceptionMapper extends JsonExceptionMapperBase<JsonParseException> {

  public JsonParseExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }

}
