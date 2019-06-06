package org.col.resources;

import java.io.*;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import io.dropwizard.auth.Auth;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.ColUser;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SectorImport;
import org.col.api.vocab.Datasets;
import org.col.assembly.AssemblyCoordinator;
import org.col.assembly.AssemblyState;
import org.col.assembly.SyncRequest;
import org.col.command.export.AcExporter;
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
  private final AcExporter exporter;
  
  public AssemblyResource(AssemblyCoordinator assembly, AcExporter exporter) {
    this.assembly = assembly;
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
                                       @QueryParam("datasetKey") Integer datasetKey,
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
  public void sync(@PathParam("catKey") int catKey, SyncRequest request, @Auth ColUser user) {
    requireDraft(catKey);
    assembly.sync(request, user);
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
  @Produces(MediaType.TEXT_PLAIN)
  public Response exportAC(@PathParam("catKey") int catKey, @Auth ColUser user) {
    requireDraft(catKey);
  
    StreamingOutput stream = new StreamingOutput() {
      @Override
      public void write(OutputStream os) throws IOException, WebApplicationException {
        Writer writer = new OutputStreamWriter(os);
        try {
          exporter.export(catKey, writer);
        } catch (Throwable e) {
          writer.append("\n\n");
          PrintWriter pw = new PrintWriter(writer);
          e.printStackTrace(pw);
          pw.flush();
        }
        writer.flush();
      }
    };
    return Response.ok(stream).build();
  }

  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
