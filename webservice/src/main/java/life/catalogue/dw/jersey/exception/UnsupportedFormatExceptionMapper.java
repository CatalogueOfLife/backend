package life.catalogue.dw.jersey.exception;

import life.catalogue.img.UnsupportedFormatException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a UnsupportedFormatException into a http 400 bad request.
 */
@Provider
public class UnsupportedFormatExceptionMapper extends JsonExceptionMapperBase<UnsupportedFormatException> {
  
  public UnsupportedFormatExceptionMapper() {
    super(Response.Status.BAD_REQUEST, false, false, "Unsupported format");
  }
}
