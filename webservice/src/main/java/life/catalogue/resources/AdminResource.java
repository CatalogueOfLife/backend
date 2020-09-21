package life.catalogue.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import io.dropwizard.auth.Auth;
import io.dropwizard.lifecycle.Managed;
import life.catalogue.WsServerConfig;
import life.catalogue.admin.MetricsUpdater;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.assembly.AssemblyState;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobExecutor;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.gbifsync.GbifSync;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.ContinuousImporter;
import life.catalogue.importer.ImportManager;
import life.catalogue.importer.ImportRequest;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.RematchJob;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.Min;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
  // managed background processes
  private final ImportManager importManager;
  private final ContinuousImporter continuousImporter;
  private final GbifSync gbifSync;
  private final AssemblyCoordinator assembly;
  private final NameIndex namesIndex;
  private final JobExecutor exec;



  public AdminResource(SqlSessionFactory factory, AssemblyCoordinator assembly, DownloadUtil downloader, WsServerConfig cfg, ImageService imgService, NameIndex ni,
                       NameUsageIndexService indexService, ContinuousImporter continuousImporter, ImportManager importManager, GbifSync gbifSync,
                       NameIndex namesIndex, JobExecutor executor) {
    this.factory = factory;
    this.assembly = assembly;
    this.imgService = imgService;
    this.ni = ni;
    this.cfg = cfg;
    this.downloader = downloader;
    this.indexService = indexService;
    this.gbifSync = gbifSync;
    this.continuousImporter = continuousImporter;
    this.importManager = importManager;
    this.namesIndex = namesIndex;
    this.exec = executor;
  }
  
  public static class BackgroundProcesses {
    public boolean gbifSync;
    public boolean scheduler;
    public boolean importer; // import manager & names index
    @Nullable
    @Min(1)
    public Integer importerThreads;
  }

  @GET
  @Path("/job")
  public List<BackgroundJob> jobQueue() {
    return exec.getQueue();
  }

  @GET
  @Path("/job/{key}")
  public BackgroundJob job(@PathParam("key") UUID key) {
    return exec.getJob(key);
  }

  @DELETE
  @Path("/job/{key}")
  public BackgroundJob cancel(@PathParam("key") UUID key, @Auth User user) {
    return exec.cancel(key, user.getKey());
  }


  @GET
  @Path("/assembly")
  public AssemblyState globalState() {
    return assembly.getState();
  }

  @GET
  @Path("/background")
  @PermitAll
  public BackgroundProcesses getBackground() {
    BackgroundProcesses back = new BackgroundProcesses();
    back.scheduler = continuousImporter.hasStarted();
    back.importer = importManager.hasStarted();
    back.gbifSync = gbifSync.hasStarted();
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
    
    if (curr.scheduler != back.scheduler) {
      if (cfg.importer.continousImportPolling < 1) {
        // we started the server with no polling, give it a reasonable default
        cfg.importer.continousImportPolling = 10;
      }
      LOG.info("Set continuous importer to active={}", back.scheduler);
      startStopManaged(continuousImporter, back.scheduler);
    }

    if (curr.importer != back.importer) {
      if (back.importerThreads != null && back.importerThreads > 0) {
        cfg.importer.threads = back.importerThreads;
      }
      LOG.info("Set import manager with {} threads & names index to active={}", cfg.importer.threads, back.importer);
      // order is important
      if (back.importer) {
        namesIndex.start();
        importManager.start();
      } else {
        importManager.stop();
        namesIndex.stop();
      }
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
  public BackgroundJob updateAllLogos(@Auth User user) {
    return runJob(LogoUpdateJob.updateAllAsync(factory, downloader, cfg.normalizer::scratchFile, imgService, user.getKey()));
  }

  @POST
  @Path("/metrics-update")
  public BackgroundJob updateMetrics(@QueryParam("datasetKey") Integer datasetKey, @Auth User user) {
    return runJob(new MetricsUpdater(factory, cfg, datasetKey, user));
  }

  @POST
  @Path("/counter-update")
  public BackgroundJob updateCounter(@Auth User user) {
    return runJob(new UsageCountJob(user, JobPriority.HIGH));
  }

  @POST
  @Path("/reindex")
  public BackgroundJob reindex(@QueryParam("datasetKey") Integer datasetKey, @QueryParam("prio") JobPriority priority, RequestScope req, @Auth User user) {
    if (req == null) {
      req = new RequestScope();
    }
    if (datasetKey != null) {
      req.setDatasetKey(datasetKey);
    }
    if (req.getDatasetKey() == null && !req.getAll()) {
      throw new IllegalArgumentException("Request parameter all or datasetKey must be provided");
    }
    return runJob(new IndexJob(req, user, priority));
  }

  @POST
  @Path("/rematch")
  public BackgroundJob rematch(@QueryParam("datasetKey") Integer datasetKey, @Auth User user) {
    if (datasetKey == null) {
      return runJob(RematchJob.all(user,factory,ni));
    } else {
      return runJob(RematchJob.one(user,factory,ni, datasetKey));
    }
  }

  @DELETE
  @Path("/reindex")
  public int createEmptyIndex(@Auth User user) {
    LOG.warn("Drop and recreate empty search index by {}", user);
    return indexService.createEmptyIndex();
  }

  @POST
  @Path("/reimport")
  public BackgroundJob reimport(@Auth User user) {
    return runJob(new ReimportJob(user));
  }

  private BackgroundJob runJob(BackgroundJob job){
    exec.submit(job);
    return job;
  }

  /**
   * Updates the usage counter for all managed datasets.
   */
  class UsageCountJob extends BackgroundJob {

    UsageCountJob(User user, JobPriority priority) {
      super(priority, user.getKey());
    }

    @Override
    public void execute() {
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
        for (int key : dm.keys(DatasetOrigin.MANAGED)) {
          int cnt = dpm.updateUsageCounter(key);
          LOG.info("Updated usage counter for managed dataset {} to {}", key, cnt);
        }
      }
    }
  }

  class IndexJob extends BackgroundJob {
    @JsonProperty
    private final RequestScope req;

    IndexJob(RequestScope req, User user, JobPriority priority) {
      super(priority, user.getKey());
      this.req = req;
    }
  
    @Override
    public void execute() {
      // cleanup
      try {
        if (req.getDatasetKey() != null) {
          LOG.info("Reindex dataset {} by {}", req.getDatasetKey(), getUserKey());
          indexService.indexDataset(req.getDatasetKey());
        } else {
          LOG.warn("Reindex all datasets by {}", getUserKey());
          indexService.indexAll();
        }
      } catch (RuntimeException e){
        LOG.error("Error reindexing", e);
      }
    }
  }

  /**
   * Submits import jobs for all existing archives.
   * Throttles the submission so the import manager does not exceed its queue
   */
  class ReimportJob extends BackgroundJob {
    @JsonProperty
    private int counter;

    public ReimportJob(User user) {
      super(user.getKey());
    }

    @Override
    public void execute() {
      final List<Integer> keys;
      try (SqlSession session = factory.openSession()) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        keys = dm.keys();
      }

      LOG.warn("Reimporting all {} datasets from their last local copy", keys.size());
      final List<Integer> missed = new ArrayList<>();
      counter = 0;
      for (int key : keys) {
        try {
          while (importManager.queueSize() + 5 > cfg.importer.maxQueue) {
            TimeUnit.MINUTES.sleep(1);
          }
          // does a local archive exist?
          File f = cfg.normalizer.source(key);
          if (f.exists()) {
            ImportRequest req = new ImportRequest(key, getUserKey(), true, false,true);
            importManager.submit(req);
            counter++;
          } else {
            missed.add(key);
            LOG.warn("No local archive exists for dataset {}. Do not reimport", key);
          }

        } catch (IllegalArgumentException e) {
          missed.add(key);
          LOG.warn("Cannot reimport dataset {}", key, e);

        } catch (InterruptedException e) {
          LOG.warn("Reimporting interrupted", e);
          break;
        }
      }
      LOG.info("Scheduled {} datasets out of {} for reimporting. Missed {} datasets without an archive or other reasons", counter, keys.size(),  missed.size());
      LOG.info("Missed keys: {}", Joiner.on(", ").join(missed));
    }
  }

}
