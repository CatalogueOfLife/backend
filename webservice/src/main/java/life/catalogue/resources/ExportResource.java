package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.User;
import life.catalogue.dw.auth.Roles;
import life.catalogue.release.AcExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/dataset/{datasetKey}/export")
@Produces(MediaType.APPLICATION_JSON)
public class ExportResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ExportResource.class);
  private final AcExporter exporter;

  public ExportResource(AcExporter exporter) {
    this.exporter = exporter;
  }

  @POST
  @Path("export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public boolean export(@PathParam("datasetKey") int datasetKey, @Auth User user) {
    return exportAC(datasetKey, user);
  }

  private boolean exportAC(int key, User user) {
    try {
      exporter.export(key);
      return true;

    } catch (Throwable e) {
      LOG.error("Error exporting dataset {}", key, e);
    }
    return false;
  }

}
