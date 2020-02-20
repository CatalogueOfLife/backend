package life.catalogue.resources;

import com.google.common.collect.ImmutableList;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SubjectRematcher;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.name.index.NameUsageIndexService;
import life.catalogue.release.AcExporter;
import life.catalogue.release.CatalogueRelease;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Path("/assembly")
@Produces(MediaType.APPLICATION_JSON)
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final AssemblyCoordinator assembly;
  private final AcExporter exporter;
  private final DatasetImportDao diDao;
  private final TaxonDao tdao;
  private final NameUsageIndexService indexService;
  private CatalogueRelease release;
  private final SqlSessionFactory factory;
  private static final ThreadPoolExecutor RELEASE_EXEC = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
      new ArrayBlockingQueue(1), new NamedThreadFactory("col-release"), new ThreadPoolExecutor.DiscardPolicy());
  
  public AssemblyResource(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, TaxonDao tdao, AssemblyCoordinator assembly, AcExporter exporter) {
    this.indexService = indexService;
    this.assembly = assembly;
    this.exporter = exporter;
    this.factory = factory;
    this.diDao = diDao;
    this.tdao = tdao;
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
  public void sync(@PathParam("catKey") int catKey, RequestScope request, @Auth ColUser user) {
    requireManagedNoLock(catKey);
    assembly.sync(catKey, request, user);
  }
  
  @DELETE
  @Path("/{catKey}/sync/{sectorKey}")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public void deleteSync(@PathParam("catKey") int catKey, @PathParam("sectorKey") int sectorKey, @Auth ColUser user) {
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
  
  @GET
  @Path("/{catKey}/release")
  public String releaseState(@PathParam("catKey") int catKey) {
    requireManagedNoLock(catKey);
    if (release == null) {
      return "idle";
    }
    return release.getState();
  }
  
  @POST
  @Path("/{catKey}/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer release(@PathParam("catKey") int catKey, @Auth ColUser user) {
    requireManagedNoLock(catKey);
  
    if (release != null) {
      throw new IllegalStateException("Release "+release.getSourceDatasetKey() + " to " + release.getReleaseKey() + " is already running");
    }
  
    release = CatalogueRelease.release(factory, indexService, exporter, diDao, catKey, user.getKey());
    final int key = release.getReleaseKey();
  
    CompletableFuture.runAsync(release, RELEASE_EXEC).thenApply(x -> {
      // clear release reference when job is done
      release = null;
      return x;
    });

    return key;
  }

  @POST
  @Deprecated
  @Path("/{catKey}/export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String export(@PathParam("catKey") int catKey, @Auth ColUser user) {
    requireManaged(catKey, true);
  
    life.catalogue.release.Logger logger = new life.catalogue.release.Logger(LOG);
    try {
      exporter.export(catKey, logger);
    } catch (Throwable e) {
      LOG.error("Error exporting catalogue {}", catKey, e);
      logger.log("\n\nERROR!");
      if (e.getMessage() != null) {
        logger.log(e.getMessage());
      }
    }
    return logger.toString();
  }
  
  @POST
  @Path("/{catKey}/rematch")
  public SubjectRematcher rematch(@PathParam("catKey") int catKey, RematchRequest req, @Auth ColUser user) {
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
