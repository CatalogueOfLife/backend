package org.col.dw.jersey.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.col.img.UnsupportedFormatException;

/**
 * Converts a UnsupportedFormatException into a http 400 bad request.
 */
@Provider
public class UnsupportedFormatExceptionMapper extends JsonExceptionMapperBase<UnsupportedFormatException> {

  public UnsupportedFormatExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }
}
