package life.catalogue.resources;

import life.catalogue.dw.jersey.MoreMediaTypes;
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
  // there are many unofficial mime types around for zip, support them all
  @Produces({
    MediaType.APPLICATION_OCTET_STREAM,
    MediaType.APPLICATION_JSON, // allow JSON too as this is the API default which will be used when no Accept header is present
    MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3
  })
  public Response getExport(@PathParam("id") UUID key) {
    return Response.status(Response.Status.FOUND)
      .location(exportManager.archiveURI(key))
      .build();
  }

}
