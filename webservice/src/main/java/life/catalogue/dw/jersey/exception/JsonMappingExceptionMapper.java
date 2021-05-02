package life.catalogue.dw.jersey.exception;

import com.fasterxml.jackson.databind.JsonMappingException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a JsonMappingException into a http 400 bad request.
 */
@Provider
public class JsonMappingExceptionMapper extends JsonExceptionMapperBase<JsonMappingException> {
  
  public JsonMappingExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
