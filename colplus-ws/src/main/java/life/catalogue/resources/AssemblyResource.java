package life.catalogue.resources;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SubjectRematcher;
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

@Path("/assembly/{catKey}")
@Produces(MediaType.APPLICATION_JSON)
public class AssemblyResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyResource.class);
  private final AssemblyCoordinator assembly;
  private final AcExporter exporter;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private CatalogueRelease release;
  private final SqlSessionFactory factory;
  private static final ThreadPoolExecutor RELEASE_EXEC = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
      new ArrayBlockingQueue(1), new NamedThreadFactory("col-release"), new ThreadPoolExecutor.DiscardPolicy());
  
  public AssemblyResource(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, AssemblyCoordinator assembly, AcExporter exporter) {
    this.indexService = indexService;
    this.assembly = assembly;
    this.exporter = exporter;
    this.factory = factory;
    this.diDao = diDao;
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
  
  @GET
  @Path("/release")
  public String releaseState(@PathParam("catKey") int catKey) {
    requireDraft(catKey);
    if (release == null) {
      return "idle";
    }
    return release.getState();
  }
  
  @POST
  @Path("/release")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Integer release(@PathParam("catKey") int catKey, @Auth ColUser user) {
    requireDraft(catKey);
  
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
  @Path("/export")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public String export(@PathParam("catKey") int catKey, @Auth ColUser user) {
    requireManaged(catKey);
  
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
  
  private void requireManaged(int catKey) {
    try (SqlSession s = factory.openSession()) {
      Dataset d = s.getMapper(DatasetMapper.class).get(catKey);
      if (d.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Only managed datasets can be assembled. Dataset " + catKey + " is of origin " + d.getOrigin());
      }
    }
  }
}
