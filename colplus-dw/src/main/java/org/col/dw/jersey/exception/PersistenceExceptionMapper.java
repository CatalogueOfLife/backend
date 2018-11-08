package org.col.dw.jersey.exception;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import org.apache.ibatis.exceptions.PersistenceException;
import org.postgresql.util.PSQLException;

/**
 * Checks PersistenceExceptions for certain known conditions that are not server errors.
 * 1) see if they are caused by missing dataset partitions which are http 404
 * 2) check for violated unique constraints which are considered 400.
 */
@Provider
public class PersistenceExceptionMapper extends LoggingExceptionMapper<PersistenceException> {
  
  private final static Pattern RELATION = Pattern.compile("relation \"[a-z_]+_([0-9]+)\" does not exist");
  
  @Override
  public Response toResponse(PersistenceException e) {
    if (e.getCause() != null && e.getCause() instanceof PSQLException) {
      PSQLException pe = (PSQLException) e.getCause();
      // All Psql Error codes starting with 23 are constraint violations.
      // https://www.postgresql.org/docs/9.4/static/errcodes-appendix.html
      if (pe.getSQLState() != null && pe.getSQLState().startsWith("23")) {
        return JsonExceptionMapperBase.jsonErrorResponse(Response.Status.BAD_REQUEST, e.getMessage());
      }
    }
    
    Matcher m = RELATION.matcher(e.getCause().getMessage());
    if (m.find()) {
      return JsonExceptionMapperBase.jsonErrorResponse(Response.Status.NOT_FOUND,
          "Dataset " + Integer.parseInt(m.group(1)) + " does not exist");
    }
    
    return super.toResponse(e);
  }
  
}
