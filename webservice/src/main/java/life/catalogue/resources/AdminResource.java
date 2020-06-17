package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.lifecycle.Managed;
import life.catalogue.WsServerConfig;
import life.catalogue.admin.MetricsUpdater;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.gbifsync.GbifSync;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.ContinuousImporter;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.function.Supplier;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN})
public class AdminResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AdminResource.class);
  private final SqlSessionFactory factory;
  private final DownloadUtil downloader;
  private final WsServerConfig cfg;
  private final ImageService imgService;
  private final NameUsageIndexService indexService;
  private final NameIndex ni;
  private Thread thread;
  // background processes
  private final ContinuousImporter continuousImporter;
  private final GbifSync gbifSync;
  private final AssemblyCoordinator assembly;


  public AdminResource(SqlSessionFactory factory, AssemblyCoordinator assembly, DownloadUtil downloader, WsServerConfig cfg, ImageService imgService, NameIndex ni,
                       NameUsageIndexService indexService, ContinuousImporter continuousImporter, GbifSync gbifSync) {
    this.factory = factory;
    this.assembly = assembly;
    this.imgService = imgService;
    this.ni = ni;
    this.cfg = cfg;
    this.downloader = downloader;
    this.indexService = indexService;
    this.gbifSync = gbifSync;
    this.continuousImporter = continuousImporter;
  }
  
  public static class BackgroundProcesses {
    public boolean gbifSync;
    public boolean importer;
  }

  @GET
  @Path("/assembly")
  public AssemblyState globalState() {
    return assembly.getState();
  }

  @GET
  @Path("/background")
  public BackgroundProcesses getBackground() {
    BackgroundProcesses back = new BackgroundProcesses();
    back.importer = continuousImporter.isActive();
    back.gbifSync = gbifSync.isActive();
    return back;
  }
  
  @PUT
  @Path("/background")
  public void setBackground(BackgroundProcesses back) throws Exception {
    BackgroundProcesses curr = getBackground();
    
    if (curr.gbifSync != back.gbifSync) {
      if (cfg.gbif.syncFrequency < 1) {
        // we started the server with no syncing, give it a reasonable default in hours
        cfg.gbif.syncFrequency = 6;
      }
      LOG.info("Set GBIF Sync to active={}", back.gbifSync);
      startStopManaged(gbifSync, back.gbifSync);
    }
    
    if (curr.importer != back.importer) {
      if (cfg.importer.continousImportPolling < 1) {
        // we started the server with no polling, give it a reasonable default
        cfg.importer.continousImportPolling = 10;
      }
      LOG.info("Set continuous importer to active={}", back.importer);
      startStopManaged(continuousImporter, back.importer);
    }
  }
  
  private static void startStopManaged(Managed m, boolean start) throws Exception {
    if (start) {
      m.start();
    } else {
      m.stop();
    }
  }
  
  @POST
  @Path("/logo-update")
  public String updateAllLogos() {
    return runJob("logo-updater", () -> LogoUpdateJob.updateAllAsync(factory, downloader, cfg.normalizer::scratchFile, imgService));
  }

  @POST
  @Path("/metrics-update")
  public String updateAllFileMetrics(@QueryParam("datasetKey") Integer datasetKey) {
    return runJob("metrics-updater", () -> new MetricsUpdater(factory, cfg, datasetKey));
  }

  private String runJob(String threadName, Supplier<Runnable> supplier){
    if (thread != null) {
      throw new IllegalStateException("A background thread " + thread.getName() + " is already running");
    }
    Runnable job = supplier.get();
    thread = new Thread(new JobWrapper(job), threadName);
    thread.setDaemon(false);
    thread.start();
    return "Started " + job.getClass().getSimpleName();
  }
  
  @POST
  @Path("/reindex")
  public String reindex(RequestScope req, @Auth User user) {
    if (req == null || (req.getDatasetKey() == null && !req.getAll())) {
      throw new IllegalArgumentException("Request parameter all or datasetKey must be provided");
    }
    return runJob("es-reindexer", () -> new IndexJob(req, user));
  }
  
  @POST
  @Path("/loadNamesIndexSinceStart")
  public void loadNidxSince(@Auth User user) {
    ((NameIndexImpl) ni).loadFromPgSinceStart();
  }
  
  class JobWrapper implements Runnable {
    private final Runnable job;
  
    JobWrapper(Runnable job) {
      this.job = job;
    }
  
    @Override
    public void run() {
      try {
        job.run();
      } catch (Exception e){
        LOG.error("Error running job {}", job.getClass().getSimpleName(), e);
      } finally {
        thread = null;
      }
    }
  }

  class IndexJob implements Runnable {
    private final RequestScope req;
    private final User user;
  
    IndexJob(RequestScope req, User user) {
      this.req = req;
      this.user = user;
    }
  
    @Override
    public void run() {
      // cleanup
      try {
        if (req.getDatasetKey() != null) {
          LOG.info("Reindex dataset {} by {}", req.getDatasetKey(), user);
          indexService.indexDataset(req.getDatasetKey());
        } else {
          LOG.warn("Reindex all datasets by {}", user);
          indexService.indexAll();
        }
      } catch (Exception e){
        LOG.error("Error reindexing", e);
      }
    }
  }
}
