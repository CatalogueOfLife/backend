package org.col.admin.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.admin.assembly.AssemblyCoordinator;
import org.col.admin.assembly.AssemblyState;
import org.col.api.model.ColUser;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SectorImport;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.SectorImportMapper;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/assembly/{catKey}")
@Produces(MediaType.APPLICATION_JSON)
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
  
  @GET
  @Path("/sync/sector")
  public ResultPage<SectorImport> list(@QueryParam("sectorKey") Integer sectorKey,
                                       @QueryParam("state") List<SectorImport.State> states,
                                       @QueryParam("running") Boolean running,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    if (running != null) {
      states = running ? SectorImport.runningStates() : SectorImport.finishedStates();
    }
    SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
    return new ResultPage<>(page, sim.count(sectorKey, states), sim.list(sectorKey, states, page));
  }
  
  @POST
  @Path("/sync/sector/{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("catKey") int catKey, @PathParam("key") int sectorKey, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.syncSector(sectorKey, user);
  }
  
  @DELETE
  @Path("/sync/sector/{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void delete(@PathParam("catKey") int catKey, @PathParam("key") int sectorKey, @Auth ColUser user) {
    assembly.cancel(sectorKey, user);
  }
  
  @GET
  @Path("/sync/sector/{key}/{attempt}")
  public SectorImport getImportAttempt(@PathParam("key") int key,
                                        @PathParam("attempt") int attempt,
                                        @Context SqlSession session) {
    return session.getMapper(SectorImportMapper.class).get(key, attempt);
  }
  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
