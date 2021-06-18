package life.catalogue.dw.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a UnsupportedOperationException into a http 501 not implemented.
 */
@Provider
public class UnsupportedOperationExceptionMapper extends JsonExceptionMapperBase<UnsupportedOperationException> {
  
  public UnsupportedOperationExceptionMapper() {
    super(Response.Status.NOT_IMPLEMENTED, false, false, "Unsupported operation");
  }
  
  @Override
  String message(UnsupportedOperationException e) {
    String msg = super.message(e);
    return msg == null ? "API method not implemented yet" : msg;
  }
}
