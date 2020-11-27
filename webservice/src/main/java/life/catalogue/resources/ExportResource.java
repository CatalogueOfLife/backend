package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.User;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.exporter.ExportManager;
import life.catalogue.exporter.ExportRequest;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/export")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExportResource {
  private final ExportManager exportManager;

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ExportResource.class);

  public ExportResource(SqlSessionFactory factory, ExportManager exportManager, DatasetImportDao diDao, WsServerConfig cfg) {
    this.exportManager = exportManager;
  }

  @POST
  public UUID export(@Valid ExportRequest req, @Auth User user) {
    return exportManager.sumit(req);
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getExport(@PathParam("key") UUID key) {
    return Response.status(Response.Status.FOUND)
      .location(exportManager.archiveURI(key))
      .build();
  }

}
