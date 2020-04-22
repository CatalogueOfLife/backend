package life.catalogue.resources;

import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.dao.SubjectRematcher;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.release.AcExporter;
import life.catalogue.release.ReleaseManager;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/assembly")
@Produces(MediaType.APPLICATION_JSON)
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final AssemblyCoordinator assembly;
  private final AcExporter exporter;
  private final TaxonDao tdao;
  private final SqlSessionFactory factory;
  private final ReleaseManager releaseManager;

  public AssemblyResource(SqlSessionFactory factory, TaxonDao tdao, AssemblyCoordinator assembly, AcExporter exporter, ReleaseManager releaseManager) {
    this.assembly = assembly;
    this.exporter = exporter;
    this.factory = factory;
    this.tdao = tdao;
    this.releaseManager = releaseManager;
  }

  @GET
  public AssemblyState globalState() {
    return assembly.getState();
  }

  @GET
  @Path("/{catKey}")
  public AssemblyState catState(@PathParam("catKey") int catKey) {
    requireManagedNoLock(catKey);
    return assembly.getState(catKey);
  }
  
  @GET
  @Path("/{catKey}/sync")
  public ResultPage<SectorImport> list(@PathParam("catKey") int catKey,
                                       @QueryParam("sectorKey") Integer sectorKey,
                                       @QueryParam("datasetKey") Integer datasetKey,
                                       @QueryParam("state") List<SectorImport.State> states,
                                       @QueryParam("running") Boolean running,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    if (running != null) {
      states = running ? SectorImport.runningStates() : SectorImport.finishedStates();
    }
    final List<SectorImport.State> immutableStates = ImmutableList.copyOf(states);
    SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
    List<SectorImport> imports = sim.list(sectorKey, catKey, datasetKey, states, page);
    return new ResultPage<>(page, imports, () -> sim.count(sectorKey, catKey, datasetKey, immutableStates));
  }
  
  @POST
  @Path("/{catKey}/sync")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void sync(@PathParam("catKey") int catKey, RequestScope request, @Auth User user) {
    requireManagedNoLock(catKey);
    assembly.sync(catKey, request, user);
  }
  
  @DELETE
  @Path("/{catKey}/sync/{sectorKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSync(@PathParam("catKey") int catKey, @PathParam("sectorKey") int sectorKey, @Auth User user) {
    requireManagedNoLock(catKey);
    assembly.cancel(sectorKey, user);
  }
  
  @GET
  @Path("/{catKey}/sync/{sectorKey}/{attempt}")
  public SectorImport getImportAttempt(@PathParam("catKey") int catKey,
                                       @PathParam("sectorKey") int sectorKey,
                                       @PathParam("attempt") int attempt,
                                       @Context SqlSession session) {
    requireManagedNoLock(catKey);
    return session.getMapper(SectorImportMapper.class).get(sectorKey, attempt);
  }
  
  @POST
  @Path("/{catKey}/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer release(@PathParam("catKey") int catKey, @Auth User user) {
    requireManagedNoLock(catKey);
    return releaseManager.release(catKey, user);
  }

  @POST
  @Deprecated
  @Path("/{catKey}/export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public boolean export(@PathParam("catKey") int catKey, @Auth User user) {
    requireManaged(catKey, true);
  
    try {
      exporter.export(catKey);
      return true;
    } catch (Throwable e) {
      LOG.error("Error exporting catalogue {}", catKey, e);
    }
    return false;
  }
  
  @POST
  @Path("/{catKey}/rematch")
  public SubjectRematcher rematch(@PathParam("catKey") int catKey, RematchRequest req, @Auth User user) {
    requireManagedNoLock(catKey);
    SubjectRematcher matcher = new SubjectRematcher(factory, catKey, user.getKey());
    matcher.match(req);
    return matcher;
  }

  @POST
  @Path("/{catKey}/sector-count-update")
  public boolean updateAllSectorCounts(@PathParam("catKey") int catKey) {
    requireManagedNoLock(catKey);
    try (SqlSession session = factory.openSession()) {
      tdao.updateAllSectorCounts(catKey, factory);
      session.commit();
      return true;
    }
  }

  private void requireManagedNoLock(int catKey) {
    requireManaged(catKey, false);
  }

  private void requireManaged(int catKey, boolean allowLocked) {
    try (SqlSession s = factory.openSession()) {
      Dataset d = s.getMapper(DatasetMapper.class).get(catKey);
      if (d.getDeleted() != null) {
        throw new IllegalArgumentException("The dataset " + catKey + " is deleted and cannot be assembled.");
      }
      if (d.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Only managed datasets can be assembled. Dataset " + catKey + " is of origin " + d.getOrigin());
      }
      if (!allowLocked && d.isLocked()) {
        throw new IllegalArgumentException("The dataset " + catKey + " is locked and cannot be assembled.");
      }
    }
  }
}
