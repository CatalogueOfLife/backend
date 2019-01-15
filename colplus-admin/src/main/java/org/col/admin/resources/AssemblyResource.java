package org.col.admin.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.col.admin.assembly.AssemblyCoordinator;
import org.col.admin.assembly.AssemblyState;
import org.col.api.model.ColUser;
import org.col.api.vocab.Datasets;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/assembly/{catKey}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN, Roles.EDITOR})
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final AssemblyCoordinator assembly;
  
  public AssemblyResource(AssemblyCoordinator assembly) {
    this.assembly = assembly;
  }
  
  @GET
  @Path("/sync")
  public AssemblyState state(@PathParam("catKey") int catKey) {
    requireDraft(catKey);
    return assembly.getState();
  }
  
  @POST
  @Path("/sync/sector/{key}")
  public void sync(@PathParam("catKey") int catKey, @QueryParam("key") int sectorKey, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.syncSector(sectorKey, user);
  }
  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
