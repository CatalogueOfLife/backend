package org.col.dw.jersey.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import org.apache.ibatis.exceptions.PersistenceException;

/**
 * Checks PersistenceExceptions to see if they are caused by missing dataset partitions.
 */
@Provider
public class PersistenceExceptionMapper extends LoggingExceptionMapper<PersistenceException> {

  private final static Pattern RELATION = Pattern.compile("relation \"[a-z_]+_([0-9]+)\" does not exist");

  private Integer datasetNotFound(PersistenceException e) {
    System.out.println(e.getMessage());
    System.out.println(e.getCause().getMessage());
    Matcher m = RELATION.matcher(e.getCause().getMessage());
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    return null;
  }

  @Override
  public Response toResponse(PersistenceException e) {
    Integer datasetKey = datasetNotFound(e);
    if (datasetKey != null) {
      return JsonExceptionMapperBase.jsonErrorResponse(Response.Status.NOT_FOUND, "Dataset "+datasetKey+" does not exist");
    } else {
      return super.toResponse(e);
    }
  }
}
