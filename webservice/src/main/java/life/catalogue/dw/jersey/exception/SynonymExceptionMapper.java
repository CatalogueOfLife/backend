package life.catalogue.dw.jersey.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.dropwizard.jersey.errors.ErrorMessage;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Converts a {@link SynonymException} into a 404.
 */
@Provider
public class SynonymExceptionMapper extends JsonExceptionMapperBase<SynonymException> {
  private static final Logger LOG = LoggerFactory.getLogger(SynonymExceptionMapper.class);
  private static final Response.Status STATUS = Response.Status.NOT_FOUND;

  public SynonymExceptionMapper() {
    super(STATUS, true, false, null);
  }

  @Override
  public Response toResponse(SynonymException ex) {
    LOG.debug("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
    return Response
      .status(STATUS)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new SynonymExceptionMessage(ex))
      .build();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class SynonymExceptionMessage extends ErrorMessage {
    private final DSID<String> acceptedKey;

    public SynonymExceptionMessage(SynonymException e) {
      super(STATUS.getStatusCode(), trimToNull(e.getMessage()));
      this.acceptedKey = e.acceptedKey;
    }

    public String getAcceptedId() {
      return acceptedKey.getId();
    }
  }
}
