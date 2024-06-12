package life.catalogue.dw.jersey.exception;

import life.catalogue.db.PgUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;

import static life.catalogue.dw.jersey.exception.JsonExceptionMapperBase.jsonErrorResponse;

/**
 * Checks PersistenceExceptions for certain known conditions that are not server errors.
 * 1) see if they are caused by missing dataset partitions which are http 404
 * 2) check for violated unique constraints which are considered 400.
 */
@Provider
public class PersistenceExceptionMapper extends LoggingExceptionMapper<PersistenceException> {
  private static final Logger LOG = LoggerFactory.getLogger(PersistenceExceptionMapper.class);
  
  private static final Pattern RELATION = Pattern.compile("relation \"[a-z_]+_([0-9]+)\" does not exist");
  private static final Pattern UNIQUE = Pattern.compile("unique constraint \"([a-z]+)_");
  private static final Pattern UNIQUE_DETAILS = Pattern.compile("Detail: Key \\(([a-z_-]+)\\)=\\((.*)\\) already exists");
  private static final Pattern EXCLUSION = Pattern.compile("exclusion constraint \"([a-z]+)_");
  private static final Pattern EXCLUSION_DETAILS = Pattern.compile("Detail: Key \\(([a-z_-]+)\\)=\\((.*)\\) conflicts with existing key");
  public static final String CODE_NOT_FOUND = "42P01";
  public static final String EXCLUSION_CODE = "23P01";

  @Override
  public Response toResponse(PersistenceException e) {
    final Optional<String> pgCode = postgresErrorCode(e);
    if (pgCode.isPresent()) {
      PSQLException pe = (PSQLException) e.getCause();
      switch (pgCode.get()) {
        case CODE_NOT_FOUND:
          Matcher m = RELATION.matcher(pe.getMessage());
          if (m.find()) {
            int datasetKey = Integer.parseInt(m.group(1));
            LOG.debug("Missing partition tables for dataset {}", datasetKey, pe);
            return jsonErrorResponse(Response.Status.NOT_FOUND, "Dataset " + datasetKey + " does not exist");
          }
          break;

        case EXCLUSION_CODE:
          return parsePgError(pe, EXCLUSION, EXCLUSION_DETAILS);

        case PgUtils.CODE_UNIQUE:
          return parsePgError(pe, UNIQUE, UNIQUE_DETAILS);
      }
      // All PgSql Error codes starting with 23 are constraint violations.
      if (pgCode.get().startsWith("23")) {
        LOG.warn("Postgres constraint violation", pe);
        return jsonErrorResponse(Response.Status.BAD_REQUEST, "Database constraint violation", e.getMessage());
      }

      // if we have reached here we face unhandled postgres errors - lets log them
      LOG.warn("Unhandled Postgres error code {}: {}", pgCode.get(), pe.getMessage());
    }
    return super.toResponse(e);
  }

  private Response parsePgError(Exception e, Pattern p1, Pattern p2) {
    Matcher m = p1.matcher(e.getMessage());
    String entity = "Entity";
    if (m.find()) {
      entity = StringUtils.capitalize(m.group(1));

      Matcher details = p2.matcher(e.getMessage());
      if (details.find()) {
        String field = details.group(1);
        String value = details.group(2);
        LOG.debug("{} with {}='{}' already exists", field, value, entity, e);
        return jsonErrorResponse(Response.Status.BAD_REQUEST, entity + " with "+field+"='" + value + "' already exists");
      }
    }
    LOG.debug("{} already exists", entity, e);
    return jsonErrorResponse(Response.Status.BAD_REQUEST, entity + " already exists");
  }

  /**
   * https://www.postgresql.org/docs/12/errcodes-appendix.html
   */
  public static Optional<String> postgresErrorCode(Exception e) {
    if (e.getCause() instanceof PSQLException) {
      PSQLException pe = (PSQLException) e.getCause();
      if (pe.getSQLState() != null) {
        return Optional.of(pe.getSQLState());
      }
    }
    return Optional.empty();
  }
  
}
