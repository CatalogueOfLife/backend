package org.col.admin.resources;

import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import com.github.difflib.algorithm.DiffException;
import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.command.export.AcExporter;
import org.col.admin.assembly.AssemblyCoordinator;
import org.col.admin.assembly.AssemblyState;
import org.col.admin.assembly.SyncRequest;
import org.col.api.model.ColUser;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SectorImport;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.SectorImportMapper;
import org.col.db.tree.DiffReport;
import org.col.db.tree.DiffService;
import org.col.dw.auth.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/assembly/{catKey}")
@Produces(MediaType.APPLICATION_JSON)
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final AssemblyCoordinator assembly;
  private final DiffService diff;
  private final AcExporter exporter;
  
  public AssemblyResource(AssemblyCoordinator assembly, SqlSessionFactory factory, AcExporter exporter) {
    this.assembly = assembly;
    this.diff = new DiffService(factory);
    this.exporter = exporter;
  }
  
  @GET
  public AssemblyState state(@PathParam("catKey") int catKey) {
    requireDraft(catKey);
    return assembly.getState();
  }
  
  @GET
  @Path("/sync")
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
  @Path("/sync")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("catKey") int catKey, SyncRequest sector, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.syncSector(sector.getSectorKey(), user);
  }
  
  @DELETE
  @Path("/sync/{sectorKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSync(@PathParam("catKey") int catKey, @PathParam("sectorKey") int sectorKey, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.cancel(sectorKey, user);
  }
  
  @GET
  @Path("/sync/{sectorKey}/{attempt}")
  public SectorImport getImportAttempt(@PathParam("catKey") int catKey,
                                       @PathParam("sectorKey") int sectorKey,
                                       @PathParam("attempt") int attempt,
                                       @Context SqlSession session) {
    requireDraft(catKey);
    return session.getMapper(SectorImportMapper.class).get(sectorKey, attempt);
  }
  
  @GET
  @Path("/sync/{sectorKey}/treediff")
  public DiffReport diffTree(@PathParam("catKey") int catKey,
                              @PathParam("sectorKey") int sectorKey,
                              @QueryParam("attempts") String attempts,
                              @Context SqlSession session) throws DiffException {
    requireDraft(catKey);
    return diff.treeDiff(sectorKey, attempts);
  }
  
  @GET
  @Path("/sync/{sectorKey}/namesdiff")
  public DiffReport diffNames(@PathParam("catKey") int catKey,
                               @PathParam("sectorKey") int sectorKey,
                               @QueryParam("attempts") String attempts,
                               @Context SqlSession session) throws DiffException {
    requireDraft(catKey);
    return diff.namesDiff(sectorKey, attempts);
  }

  @DELETE
  @Path("/sector/{key}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSector(@PathParam("catKey") int catKey, @PathParam("key") int sectorKey, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.deleteSector(sectorKey, user);
  }
  
  @POST
  @Path("/exportAC")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void exportAC(@PathParam("catKey") int catKey, @Auth ColUser user) throws Exception {
    requireDraft(catKey);
    exporter.export(catKey);
    throw new UnsupportedOperationException("not implemented yet");
  }

  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
