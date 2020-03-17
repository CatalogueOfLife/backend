package life.catalogue.resources;

import io.dropwizard.auth.Auth;
import io.dropwizard.lifecycle.Managed;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ColUser;
import life.catalogue.api.model.RequestScope;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.gbifsync.GbifSync;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.ContinuousImporter;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexImpl;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

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
  private Thread indexingThread;
  private Thread logoThread;
  // background processes
  private final ContinuousImporter continuousImporter;
  private final GbifSync gbifSync;
  
  
  public AdminResource(SqlSessionFactory factory, DownloadUtil downloader, WsServerConfig cfg, ImageService imgService, NameIndex ni,
                       NameUsageIndexService indexService, ContinuousImporter continuousImporter, GbifSync gbifSync) {
    this.factory = factory;
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
    if (logoThread != null) {
      throw new IllegalStateException("Logo updater is already running");
    }
    LogoUpdateJob job = LogoUpdateJob.updateAllAsync(factory, downloader, cfg.normalizer::scratchFile, imgService);
    logoThread = new Thread(new LogoJob(job), "logo-updater");
    logoThread.setDaemon(false);
    logoThread.start();
    return "Started Logo Updater";
  }
  
  @POST
  @Path("/reindex")
  public void reindex(RequestScope req, @Auth ColUser user) {
    if (indexingThread != null) {
      throw new IllegalStateException("Indexing is already running");
    }
    
    if (req != null && (req.getDatasetKey() != null || req.getAll() != null && req.getAll())) {
      IndexJob job = new IndexJob(req, user);
      indexingThread = new Thread(job, "Es-Reindexer");
      indexingThread.setDaemon(false);
      indexingThread.start();
    } else {
      throw new IllegalArgumentException("Only all or datasetKey properties are supported");
    }
  }
  
  @POST
  @Path("/rematch")
  public void rematch(RequestScope req, @Auth ColUser user) {
    throw new NotImplementedException("Rematching names is not implemented yet");
  }
  
  @POST
  @Path("/loadNamesIndexSinceStart")
  public void loadNidxSince(@Auth ColUser user) {
    ((NameIndexImpl) ni).loadFromPgSinceStart();
  }
  
  class LogoJob implements Runnable {
    private final LogoUpdateJob job;
  
    LogoJob(LogoUpdateJob job) {
      this.job = job;
    }
  
    @Override
    public void run() {
      try {
        job.run();
      } catch (Exception e){
        LOG.error("Error updating all logos", e);
      } finally {
        logoThread = null;
      }
    }
  }

  class IndexJob implements Runnable {
    private final RequestScope req;
    private final ColUser user;
  
    IndexJob(RequestScope req, ColUser user) {
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
      } finally {
        indexingThread = null;
      }
    }
  }
}
