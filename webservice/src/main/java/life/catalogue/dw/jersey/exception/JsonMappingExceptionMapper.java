package life.catalogue.dw.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.JsonMappingException;

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
