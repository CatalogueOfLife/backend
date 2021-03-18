package life.catalogue.dw.jersey.exception;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.dw.jersey.exception.JsonExceptionMapperBase.jsonErrorResponse;

/**
 * Improves the last resort LoggingExceptionMapper to leave the original exception in the error message
 * so its viewable immediately in Kibana.
 */
@Provider
public class GenericExceptionMapper extends LoggingExceptionMapper<Exception> {

  @Override
  protected String formatLogMessage(long id, Throwable e) {
    return String.format("%s: Error handling a request: %016x", e.getClass().getSimpleName(), id);
  }
}
