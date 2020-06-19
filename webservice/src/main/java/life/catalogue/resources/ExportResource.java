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

/**
 * Stream dataset exports to the user.
 * If existing it uses preprepared files from the filesystem.
 * For yet non existing files we should generate and store them for later reuse.
 * If no format is given the original source is returned.
 *
 * Managed datasets can change data continously and we will need to:
 *  a) never store any version and dynamically recreate them each time
 *  b) keep a "dirty" flag that indicates the currently stored archive is not valid anymore because data has changed.
 *     Any edit would have to raise the dirty flag which therefore must be kept in memory and only persisted if it has changed.
 *     Creating an export would remove the flag - we will need a flag for each supported output format.
 *
 * Formats currently supported for the entire dataset and which are archived for reuse:
 *  - ColDP
 *  - ColDP simple (single TSV file)
 *  - DwCA
 *  - DwC simple (single TSV file)
 *  - TextTree
 *
 *  Single file formats for dynamic exports using some filter (e.g. rank, rootID, etc)
 *  - ColDP simple (single TSV file)
 *  - DwC simple (single TSV file)
 *  - TextTree
 */
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
