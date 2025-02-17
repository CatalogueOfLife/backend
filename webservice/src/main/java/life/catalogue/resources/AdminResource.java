package life.catalogue.resources;

import life.catalogue.WsServerConfig;
import life.catalogue.admin.jobs.*;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.assembly.SyncManager;
import life.catalogue.assembly.SyncState;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.collection.IterUtils;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.LineReader;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.managed.Component;
import life.catalogue.dw.managed.ManagedService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.gbifsync.GbifSyncJob;
import life.catalogue.gbifsync.GbifSyncManager;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import life.catalogue.importer.ImportManager;
import life.catalogue.matching.GlobalMatcherJob;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.RematchJob;
import life.catalogue.resources.legacy.IdMap;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Validator;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Path("/admin")
@Hidden
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
  private final NameUsageSearchService searchService;
  private boolean maintenance = false;
  private final Validator validator;
  private final DatasetDao ddao;
  private final SyncManager assembly;
  private final IdMap idMap;
  private final ImportManager importManager;
  private final GbifSyncManager gbifSync;
  private final NameIndex namesIndex;
  private final JobExecutor exec;
  private final ManagedService componedService;
  private final EventBus bus;
  private final LatestDatasetKeyCache lrCache;

  public AdminResource(SqlSessionFactory factory, LatestDatasetKeyCache lrCache, ManagedService managedService, SyncManager assembly, DownloadUtil downloader, WsServerConfig cfg, ImageService imgService, NameIndex ni,
                       NameUsageIndexService indexService, NameUsageSearchService searchService,
                       ImportManager importManager, DatasetDao ddao, GbifSyncManager gbifSync,
                       JobExecutor executor, IdMap idMap, Validator validator, EventBus bus) {
    this.factory = factory;
    this.bus = bus;
    this.componedService = managedService;
    this.ddao = ddao;
    this.assembly = assembly;
    this.imgService = imgService;
    this.namesIndex = ni;
    this.cfg = cfg;
    this.downloader = downloader;
    this.indexService = indexService;
    this.searchService = searchService;
    this.gbifSync = gbifSync;
    this.importManager = importManager;
    this.exec = executor;
    this.idMap = idMap;
    this.validator = validator;
    this.lrCache = lrCache;
  }

  @GET
  @Path("/assembly")
  public SyncState globalState() {
    return assembly.getState();
  }

  @POST
  @Path("/maintenance")
  public boolean toggleMaintenance() throws IOException {
    maintenance = !maintenance;
    try (Writer w = UTF8IoUtils.writerFromFile(cfg.statusFile)) {
      w.write(String.format("{\"maintenance\": %s}\n", maintenance));
    }
    LOG.info("Set maintenance mode={}", maintenance);
    return maintenance;
  }

  @GET
  @Path("/component")
  @PermitAll
  public Map<String, Boolean> componentState() {
    return componedService.state();
  }

  @POST
  @Path("/component/start")
  public boolean start(@QueryParam("comp") List<Component> components, @Auth User user) throws Exception {
    for (var comp : components) {
      componedService.start(comp);
    }
    return true;
  }

  @POST
  @Path("/component/stop")
  public boolean stop(@QueryParam("comp") List<Component> components, @Auth User user) throws Exception {
    for (var comp : components) {
      componedService.stop(comp);
    }
    return true;
  }

  @POST
  @Path("/component/restart")
  public boolean restart(@QueryParam("comp") List<Component> components, @Auth User user) throws Exception {
    LOG.warn("Restarting components {} by {}", StringUtils.concat(", ", components), user);
    for (var comp : components) {
      componedService.stop(comp);
      componedService.start(comp);
    }
    return true;
  }

  @POST
  @Path("/component/start-all")
  public boolean startAll() throws Exception {
    componedService.startAll();
    return true;
  }

  @POST
  @Path("/component/stop-all")
  public boolean stopAll() throws Exception {
    componedService.stopAll();
    return true;
  }

  @POST
  @Path("/component/restart-all")
  public boolean restartAll() throws Exception {
    componedService.stopAll();
    componedService.startAll();
    return true;
  }

  @POST
  @Path("/reload-idmap")
  public int reloadIdmap(@Auth User user) throws IOException {
    idMap.reload();
    return idMap.size();
  }

  @POST
  @Path("/logo-update")
  public BackgroundJob updateAllLogos(@Auth User user) {
    return runJob(LogoUpdateJob.updateAllAsync(factory, downloader, cfg.normalizer::scratchFile, imgService, user.getKey()));
  }

  @POST
  @Path("/counter-update")
  public BackgroundJob updateCounter(@Auth User user) {
    return runJob(new UsageCountJob(user.getKey(), JobPriority.HIGH, factory));
  }

  @POST
  @Path("/gbif-sync")
  @Consumes(MediaType.APPLICATION_JSON)
  public BackgroundJob syncGBIF(List<UUID> keys, @Auth User user) {
    GbifSyncJob job = new GbifSyncJob(cfg.gbif, gbifSync.getClient(), ddao, factory, user.getKey(), Set.copyOf(keys), false);
    return runJob(job);
  }

  @POST
  @Path("/gbif-sync")
  @Consumes(MediaType.TEXT_PLAIN)
  public BackgroundJob syncGBIFText(InputStream keysAsText, @Auth User user) {
    try (var lr = new LineReader(keysAsText)) {
      var keys = IterUtils.setOf(lr, UUID::fromString);
      GbifSyncJob job = new GbifSyncJob(cfg.gbif, gbifSync.getClient(), ddao, factory, user.getKey(), keys, false);
      return runJob(job);
    }
  }


  @DELETE
  @Path("/reindex")
  public int createEmptyIndex(@Auth User user) {
    LOG.warn("Drop and recreate empty search index by {}", user);
    return indexService.createEmptyIndex();
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
    return runJob(new IndexJob(req, user.getKey(), priority, indexService, bus));
  }

  @GET
  @Path("/reindex/preview")
  public Response reindexPreview(@Auth User user, @QueryParam("threshold") @DefaultValue("0") double threshold) {
    var job = new ReindexSchedulerJob(user.getKey(), threshold, factory, exec, searchService, indexService, null);
    StreamingOutput stream = os -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(os));
      job.write(writer);
      writer.flush();
    };
    return Response.ok(stream).build();
  }

  @POST
  @Path("/reindex/scheduler")
  /**
   * Reindex all datasets which have not been fully indexed before.
   */
  public BackgroundJob reindexBroken(@Auth User user, @QueryParam("threshold") @DefaultValue("0.95") double threshold) {
    return runJob(new ReindexSchedulerJob(user.getKey(), threshold, factory, exec, searchService, indexService, bus));
  }


  @POST
  @Path("/rematch")
  public BackgroundJob rematch(@QueryParam("datasetKey") List<Integer> datasetKeys,
                               @QueryParam("sectorKey") List<String> sectorKeys,
                               @QueryParam("missingOnly") boolean missingOnly,
                               @Auth User user
  ) {
    if (datasetKeys != null && !datasetKeys.isEmpty()) {
      var keys = datasetKeys.stream().mapToInt(i -> i).toArray();
      return runJob(RematchJob.some(user.getKey(), factory, namesIndex, bus, missingOnly, keys));

    } if (sectorKeys != null && !sectorKeys.isEmpty()) {
      var keys = sectorKeys.stream().map(DSID::ofInt).collect(Collectors.toList());
      return runJob(RematchJob.sector(user.getKey(),factory, namesIndex, keys));

    } else {
      throw new IllegalArgumentException("At least one datasetKey or sectorKey parameter is required");
    }
  }

  @GET
  @Path("/rematch/preview")
  public Response rematchPreview(@Auth User user, @QueryParam("threshold") @DefaultValue("0") double threshold) {
    var job = new RematchSchedulerJob(user.getKey(), threshold, factory, namesIndex, exec, null);
    StreamingOutput stream = os -> {
      Writer writer = new BufferedWriter(new OutputStreamWriter(os));
      job.write(writer);
      writer.flush();
    };
    return Response.ok(stream).build();
  }

  /**
   * Rematch all datasets which have not been fully matched before.
   */
  @POST
  @Path("/rematch/scheduler")
  public BackgroundJob rematchIncomplete(@Auth User user, @QueryParam("threshold") @DefaultValue("0") double threshold) {
    return runJob(new RematchSchedulerJob(user.getKey(), threshold, factory, namesIndex, exec, bus));
  }

  @POST
  @Path("/rematch/missing")
  /**
   * Matches all datasets which have not been fully matched before.
   */
  public BackgroundJob rematchUnmatched(@Auth User user) {
    return runJob(new GlobalMatcherJob(user.getKey(), factory, namesIndex, bus));
  }


  @DELETE
  @Path("/cache")
  public boolean clearCaches(@Auth User user) {
    LOG.info("Clear dataset info cache with {} entries by {}", DatasetInfoCache.CACHE.size(), user);
    DatasetInfoCache.CACHE.clear();
    lrCache.clear();
    return true;
  }

  @POST
  @Path("/reimport")
  public BackgroundJob reimport(@Auth User user) {
    return runJob(new ReimportJob(user, factory, importManager, cfg));
  }

  @POST
  @Path("/importArticles")
  public BackgroundJob scheduleArticleImports(@Auth User user) {
    return runJob(new ImportArticleJob(user, factory, importManager, cfg));
  }

  @POST
  @Path("rebuild-taxon-metrics")
  public BackgroundJob rebuildTaxonMetrics(@QueryParam("datasetKey") Integer datasetKey, @Auth User user) {
    Preconditions.checkArgument(datasetKey != null, "A datasetKey parameter must be given");
    return runJob(new RebuildMetricsJob(user.getKey(), factory, datasetKey));
  }

  private BackgroundJob runJob(BackgroundJob job){
    exec.submit(job);
    return job;
  }
}
