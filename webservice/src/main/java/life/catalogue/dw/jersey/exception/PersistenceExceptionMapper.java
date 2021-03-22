package life.catalogue.dw.jersey.exception;

import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.jdbc.SQL;
import org.apache.xmlbeans.impl.soap.Detail;
import org.bouncycastle.LICENSE;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.dw.jersey.exception.JsonExceptionMapperBase.jsonErrorResponse;

/**
 * Checks PersistenceExceptions for certain known conditions that are not server errors.
 * 1) see if they are caused by missing dataset partitions which are http 404
 * 2) check for violated unique constraints which are considered 400.
 */
@Provider
public class PersistenceExceptionMapper extends LoggingExceptionMapper<PersistenceException> {
  private static final Logger LOG = LoggerFactory.getLogger(PersistenceExceptionMapper.class);
  
  private final static Pattern RELATION = Pattern.compile("relation \"[a-z_]+_([0-9]+)\" does not exist");
  private final static Pattern UNIQUE = Pattern.compile("unique constraint \"([a-z]+)_");
  private final static Pattern UNIQUE_DETAILS = Pattern.compile("Detail: Key \\(([a-z_-]+)\\)=\\((.*)\\) already exists");

  @Override
  public Response toResponse(PersistenceException e) {
    if (e.getCause() != null) {
      if (e.getCause() instanceof PSQLException) {
        PSQLException pe = (PSQLException) e.getCause();
        if (pe.getSQLState() != null) {
          // https://www.postgresql.org/docs/12/errcodes-appendix.html
          if (pe.getSQLState().equals("42P01")) {
            Matcher m = RELATION.matcher(pe.getMessage());
            if (m.find()) {
              int datasetKey = Integer.parseInt(m.group(1));
              LOG.debug("Missing partition tables for dataset {}", datasetKey, pe);
              return jsonErrorResponse(Response.Status.NOT_FOUND, "Dataset " + datasetKey + " does not exist");
            }

          } else if (pe.getSQLState().equals("23505")) {
            Matcher m = UNIQUE.matcher(e.getCause().getMessage());
            String entity = "Entity";
            if (m.find()) {
              entity = StringUtils.capitalize(m.group(1));

              Matcher details = UNIQUE_DETAILS.matcher(e.getCause().getMessage());
              if (details.find()) {
                String field = details.group(1);
                String value = details.group(2);
                LOG.debug("{} with {}='{}' already exists", field, value, entity, pe);
                return jsonErrorResponse(Response.Status.BAD_REQUEST, entity + " with "+field+"='" + value + "' already exists");
              }
            }
            LOG.debug("{} already exists", entity, pe);
            return jsonErrorResponse(Response.Status.BAD_REQUEST, entity + " already exists");
          }

          // All PgSql Error codes starting with 23 are constraint violations.
          if (pe.getSQLState().startsWith("23")) {
            LOG.warn("Postgres constraint violation", pe);
            return jsonErrorResponse(Response.Status.BAD_REQUEST, "Database constraint violation", e.getMessage());
          }
        }
      }

      if (e.getCause() instanceof PSQLException) {
        PSQLException pe2 = (PSQLException) e.getCause();
        LOG.info("Postgres code {}", pe2.getSQLState());
      }


    }
    
    return super.toResponse(e);
  }
  
}
