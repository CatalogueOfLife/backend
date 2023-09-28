package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.TimeoutException;
import life.catalogue.dw.jersey.MoreStatus;

import javax.ws.rs.ext.Provider;

/**
 * Converts a TimeoutException into a http 524 response which is a cloudfare specific code.
 */
@Provider
public class TimeoutExceptionMapper extends JsonExceptionMapperBase<TimeoutException> {

  public TimeoutExceptionMapper() {
    super(MoreStatus.SERVER_TIMEOUT, false, false, null);
  }
}
