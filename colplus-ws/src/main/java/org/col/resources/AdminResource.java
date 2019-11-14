package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;
import io.dropwizard.lifecycle.Managed;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.WsServerConfig;
import org.col.api.model.ColUser;
import org.col.api.model.RequestScope;
import org.col.api.vocab.Datasets;
import org.col.common.io.DownloadUtil;
import org.col.dao.TaxonDao;
import org.col.dw.auth.Roles;
import org.col.es.name.index.NameUsageIndexService;
import org.col.gbifsync.GbifSync;
import org.col.img.ImageService;
import org.col.img.LogoUpdateJob;
import org.col.importer.ContinuousImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final TaxonDao tdao;
  private final NameUsageIndexService indexService;
  private Thread indexingThread;
  private Thread logoThread;
  // background processes
  private final ContinuousImporter continuousImporter;
  private final GbifSync gbifSync;
  
  
  public AdminResource(SqlSessionFactory factory, DownloadUtil downloader, WsServerConfig cfg, ImageService imgService,
                       NameUsageIndexService indexService, TaxonDao tdao, ContinuousImporter continuousImporter, GbifSync gbifSync) {
    this.factory = factory;
    this.imgService = imgService;
    this.cfg = cfg;
    this.downloader = downloader;
    this.tdao = tdao;
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
  @Path("/sector-count-update")
  public boolean updateAllSectorCounts() {
    try (SqlSession session = factory.openSession()) {
      tdao.updateAllSectorCounts(Datasets.DRAFT_COL, factory);
      session.commit();
      return true;
    }
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
