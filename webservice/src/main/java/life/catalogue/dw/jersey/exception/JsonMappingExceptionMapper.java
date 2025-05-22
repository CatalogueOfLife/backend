package life.catalogue.dw.jersey.exception;

import com.fasterxml.jackson.databind.JsonMappingException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts a JsonMappingException into a http 400 bad request.
 * Assumes the exception is thrown when parsing the request, not the response!
 */
@Provider
public class JsonMappingExceptionMapper extends JsonExceptionMapperBase<JsonMappingException> {
  
  public JsonMappingExceptionMapper() {
    super(Response.Status.BAD_REQUEST, "Bad JSON argument");
  }
}
