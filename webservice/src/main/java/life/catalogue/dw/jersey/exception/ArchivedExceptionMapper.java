package life.catalogue.dw.jersey.exception;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.NameUsage;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.dropwizard.jersey.errors.ErrorMessage;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * Converts a {@link ArchivedException} into a 404.
 */
@Provider
public class ArchivedExceptionMapper extends JsonExceptionMapperBase<ArchivedException> {
  private static final Logger LOG = LoggerFactory.getLogger(ArchivedExceptionMapper.class);
  private static final Response.Status STATUS = Response.Status.NOT_FOUND;

  public ArchivedExceptionMapper() {
    super(STATUS, true, false, null);
  }

  @Override
  public Response toResponse(ArchivedException ex) {
    LOG.debug("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
    return Response
      .status(STATUS)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new ArchivedMessage(ex))
      .build();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class ArchivedMessage extends ErrorMessage {
    private final ArchivedNameUsage usage;

    public ArchivedMessage(ArchivedException e) {
      super(STATUS.getStatusCode(), trimToNull(e.getMessage()));
      this.usage = e.usage;
    }

    public NameUsage getUsage() {
      return usage;
    }
  }
}
