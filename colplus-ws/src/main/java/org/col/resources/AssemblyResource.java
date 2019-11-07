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
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.assembly.AssemblyCoordinator;
import org.col.assembly.AssemblyState;
import org.col.assembly.AcExporter;
import org.col.dao.SubjectRematcher;
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
  private final SqlSessionFactory factory;
  
  public AssemblyResource(SqlSessionFactory factory, AssemblyCoordinator assembly, AcExporter exporter) {
    this.assembly = assembly;
    this.exporter = exporter;
    this.factory = factory;
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
    return new ResultPage<>(page,
        sim.count(sectorKey, Datasets.DRAFT_COL, datasetKey, states),
        sim.list(sectorKey, Datasets.DRAFT_COL, datasetKey, states, page)
    );
  }
  
  @POST
  @Path("/sync")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("catKey") int catKey, RequestScope request, @Auth ColUser user) {
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
  
  @POST
  @Path("/rematch")
  public SubjectRematcher rematch(@PathParam("catKey") int catKey, RematchRequest req, @Auth ColUser user) {
    requireDraft(catKey);
    SubjectRematcher matcher = new SubjectRematcher(factory, catKey, user.getKey());
    matcher.match(req);
    return matcher;
  }
  
  
  private static void requireDraft(int catKey) {
    if (catKey != Datasets.DRAFT_COL) {
      throw new IllegalArgumentException("Only the draft CoL can be assembled at this stage");
    }
  }
}
