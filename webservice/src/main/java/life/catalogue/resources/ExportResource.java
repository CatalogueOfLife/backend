package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.jackson.UUIDSerde;
import life.catalogue.exporter.ExportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/export")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {
  private final ExportManager exportManager;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ExportResource.class);

  public ExportResource(ExportManager exportManager) {
    this.exportManager = exportManager;
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getExport(@PathParam("id") String keyStr) {
    UUID key = UUIDSerde.from(keyStr);
    if (key != null) {
      return Response.status(Response.Status.FOUND)
        .location(exportManager.archiveURI(key))
        .build();
    }
    throw NotFoundException.notFound("Export", keyStr);
  }

}
