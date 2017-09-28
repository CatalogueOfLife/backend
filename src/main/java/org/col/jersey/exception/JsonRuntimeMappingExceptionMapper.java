package org.col.jersey.exception;


import com.fasterxml.jackson.databind.RuntimeJsonMappingException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a RuntimeJsonMappingException into a http 400 bad request.
 */
@Provider
public class JsonRuntimeMappingExceptionMapper extends JsonExceptionMapperBase<RuntimeJsonMappingException> {

  public JsonRuntimeMappingExceptionMapper() {
    super(Response.Status.BAD_REQUEST);
  }

}
