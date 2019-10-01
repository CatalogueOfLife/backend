package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.WsServerConfig;
import org.col.api.model.ColUser;
import org.col.api.model.RematchRequest;
import org.col.api.model.RequestScope;
import org.col.api.vocab.Datasets;
import org.col.common.io.DownloadUtil;
import org.col.dao.SubjectRematcher;
import org.col.dao.TaxonDao;
import org.col.dw.auth.Roles;
import org.col.es.name.index.NameUsageIndexService;
import org.col.gbifsync.GbifSync;
import org.col.img.ImageService;
import org.col.img.LogoUpdateJob;
import org.col.importer.ContinuousImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;
import io.dropwizard.lifecycle.Managed;

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
        cfg.importer.continousImportPolling = 15;
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
    LogoUpdateJob.updateAllAsync(factory, downloader, cfg.normalizer::scratchFile, imgService);
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
      indexingThread.start();
    } else {
      throw new IllegalArgumentException("Only all or datasetKey properties are supported");
    }
  }
  
  @POST
  @Path("/rematch")
  public SubjectRematcher rematch(RematchRequest req, @Auth ColUser user) {
    SubjectRematcher matcher = new SubjectRematcher(factory, user.getKey());
    matcher.match(req);
    return matcher;
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
