package life.catalogue.importer;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.util.PagingUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Setting;
import life.catalogue.assembly.SyncManager;
import life.catalogue.common.Idle;
import life.catalogue.common.Managed;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobLane;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.ExcelCsvExtractor;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.img.ImageService;
import life.catalogue.importer.store.ImportStoreFactory;
import life.catalogue.matching.IdentifierScopeResolver;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.DoiResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import jakarta.validation.Validator;

/**
 * Manages dataset import scheduling, cancellation and listing.
 * The actual queue and execution lives in the shared JobExecutors IMPORT lane.
 */
public class ImportManager implements Managed, Idle, DatasetListener {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManager.class);
  static final Comparator<DatasetImport> DI_STARTED_COMPARATOR = Comparator.comparing(DatasetImport::getStarted, Comparator.nullsFirst(Comparator.naturalOrder()));

  private boolean started;
  private SyncManager assemblyCoordinator;
  private final ImporterConfig iCfg;
  private final NormalizerConfig nCfg;
  private final DoiConfig dCfg;
  private final DownloadUtil downloader;
  private final DoiResolver resolver;
  private final SqlSessionFactory factory;
  private final NameIndex index;
  private final NameUsageIndexService indexService;
  private final ImportStoreFactory importStoreFactory;

  private final JobExecutor jobExecutor;
  private final EventBroker bus;

  private final SectorDao sDao;
  private final DatasetDao dDao;
  private final DatasetImportDao dao;
  private final DecisionDao decisionDao;
  private final ImageService imgService;
  private final Validator validator;
  private final UsageMatcherFactory matcherFactory;
  private final IdentifierScopeResolver scopeResolver;
  private final Timer importTimer;
  private final Counter failed;

  public ImportManager(ImporterConfig iCfg, NormalizerConfig nCfg, DoiConfig dCfg, MetricRegistry registry, CloseableHttpClient client, EventBroker bus,
                       SqlSessionFactory factory, NameIndex index, DatasetImportDao diao, DatasetDao dDao, SectorDao sDao, DecisionDao decisionDao,
                       NameUsageIndexService indexService, ImageService imgService, JobExecutor jobExecutor, Validator validator, DoiResolver resolver,
                       UsageMatcherFactory matcherFactory, IdentifierScopeResolver scopeResolver) {
    this.iCfg = iCfg;
    this.nCfg = nCfg;
    this.dCfg = dCfg;
    this.bus = bus;
    this.factory = factory;
    this.validator = validator;
    this.resolver = resolver;
    this.jobExecutor = jobExecutor;
    this.importStoreFactory = new ImportStoreFactory(nCfg, iCfg.threads);
    this.downloader = new DownloadUtil(client, iCfg.githubToken, iCfg.githubTokenGeoff);
    this.index = index;
    this.imgService = imgService;
    this.indexService = indexService;
    this.dDao = dDao;
    this.sDao = sDao;
    this.decisionDao = decisionDao;
    this.dao = diao;
    this.matcherFactory = matcherFactory;
    this.scopeResolver = scopeResolver;
    importTimer = registry.timer("life.catalogue.import.timer");
    failed = registry.counter("life.catalogue.import.failed");
  }

  public void setAssemblyCoordinator(SyncManager assemblyCoordinator) {
    this.assemblyCoordinator = assemblyCoordinator;
  }

  /**
   * @return all import jobs of the executor, both queued and running
   */
  private List<ImportJob> importJobs() {
    return jobExecutor.getQueueByJobClass(ImportJob.class);
  }

  private Optional<ImportJob> importJob(int datasetKey) {
    return importJobs().stream()
        .filter(job -> job.getDatasetKey() == datasetKey)
        .findFirst();
  }

  /**
   * Lists the ImportRequests of the current queue
   */
  public List<ImportRequest> queue() {
    if (!hasStarted()) {
      return Collections.emptyList();
    }
    return importJobs().stream()
        .filter(BackgroundJob::isQueued)
        .map(ImportJob::getRequest)
        .collect(Collectors.toList());
  }

  public int queueSize() {
    if (!hasStarted()) {
      return 0;
    }
    return jobExecutor.queueSize(JobLane.IMPORT);
  }

  /**
   * Pages through all queued, running and historical imports. See https://github.com/Sp2000/colplus-backend/issues/404
   */
  public ResultPage<DatasetImport> listImports(JobSearchRequest req, Page page) {
    List<DatasetImport> running = running(req.getDatasetKey(), req.getStatus());
    ResultPage<DatasetImport> historical;

    // ignore running states in imports stored in the db - otherwise we get duplicates
    Set<JobStatus> historicalStatus = req.getStatus() == null ? Collections.EMPTY_SET
        : req.getStatus().stream()
            .filter(JobStatus::isDone)
            .collect(Collectors.toSet());

    if (req.getStatus() != null && !req.getStatus().isEmpty() && historicalStatus.isEmpty()) {
      // we originally had a request for only running states. We dont get any of these from the db
      historical = new ResultPage<>(new Page(0, 0), 0, Collections.EMPTY_LIST);

    } else {
      // query historical ones at least to get the total
      req.setStatus(historicalStatus);
      if (running.size() >= page.getLimitWithOffset()) {
        // we can answer the request from the queue alone, so limit=0 to get the total count!
        historical = dao.list(req, new Page(0, 0));

      } else {
        int offset = Math.max(0, page.getOffset() - running.size());
        int limit = Math.min(page.getLimit(), page.getLimitWithOffset() - running.size());
        historical = dao.list(req, new Page(offset, limit));
      }
    }
    // merge both lists
    int runCount = running.size();
    removeOffset(running, page.getOffset());
    running.addAll(historical.getResult());
    limit(running, page.getLimit());
    return new ResultPage<>(page, historical.getTotal() + runCount, running);
  }

  @VisibleForTesting
  protected static void removeOffset(List<?> list, int offset) {
    while (offset > 0 && !list.isEmpty()) {
      list.remove(0);
      offset--;
    }
  }

  @VisibleForTesting
  protected static void limit(List<?> list, int limit) {
    if (list.size() > limit) {
      list.subList(limit, list.size()).clear();
    }
  }

  private static DatasetImport fromImportJob(ImportJob job) {
    DatasetImport di = job.getDatasetImport();
    if (di == null) {
      di = new DatasetImport();
      di.setDatasetKey(job.getDatasetKey());
      di.setJobKey(job.getKey());
      di.setAttempt(ObjectUtils.coalesce(job.getAttempt(), -1));
    }
    // reflect the live job status in the metrics for display
    di.setStatus(job.getStatus());
    di.setStep(job.getStep());
    return di;
  }

  private List<DatasetImport> running(final Integer datasetKey, final Set<JobStatus> status) {
    final List<ImportJob> jobs = importJobs();
    // make sure we have all running ones in and on top!
    List<DatasetImport> running = jobs.stream()
        .filter(BackgroundJob::isRunning)
        .map(ImportManager::fromImportJob)
        .collect(Collectors.toList());

    // Releases/duplications also write a dataset_import metrics record, but they run as regular
    // background jobs and are surfaced through the job queue, not here - so a running release is
    // not listed twice (once as import, once as job). Its finished attempt still shows up in the
    // dataset import history via the db query in listImports.
    //TODO: remove debug logs once solved why we null dates
    for (var di : running) {
      if (di.getStarted()==null) {
        LOG.warn("Running job {} {}#{} without started timestamp: {}", di.getJob(), di.getDatasetKey(), di.attempt(), di);
      }
    }
    running.sort(DI_STARTED_COMPARATOR);

    // then add the queued imports, keeping the queues priority order
    running.addAll(
        jobs.stream()
            .filter(BackgroundJob::isQueued)
            .map(ImportManager::fromImportJob)
            .collect(Collectors.toList()));

    // finally filter by dataset & status
    return running.stream()
        .filter(di -> {
          if (datasetKey != null && !Objects.equals(datasetKey, di.getDatasetKey())) {
            return false;
          }
          if (status != null && di.getStatus() != null && !status.contains(di.getStatus())) {
            return false;
          }
          return true;
        })
        .collect(Collectors.toList());
  }

  /**
   * @return true if the import queue is empty
   */
  public boolean hasEmptyQueue() {
    return queueSize() == 0;
  }

  /**
   * @return true if imports are running or queued
   */
  public boolean hasRunning() {
    return !importJobs().isEmpty();
  }

  /**
   * @return true if import for given dataset is running or queued
   */
  public boolean isRunning(int datasetKey) {
    return importJob(datasetKey).isPresent();
  }

  /**
   * Cancels a running import job by its dataset key
   */
  public void cancel(int datasetKey, int user) {
    var job = importJob(datasetKey);
    if (job.isPresent()) {
      jobExecutor.cancel(job.get().getKey(), user);
      LOG.info("Canceled import for dataset {} by user {}", datasetKey, user);

    } else {
      LOG.info("No import existing for dataset {}. Ignore", datasetKey);
    }
  }

  /**
   * @throws IllegalArgumentException if dataset was scheduled for importing already, queue was full or dataset does not
   *         exist or is of origin managed
   */
  public synchronized ImportRequest submit(final ImportRequest req) throws IllegalArgumentException {
    validDataset(req.datasetKey);
    return submitValidDataset(req);
  }

  private Path createScratchUploadFile(final int datasetKey) throws IOException {
    Path up = nCfg.scratchFile(datasetKey, "upload.zip").toPath();
    Files.createDirectories(up.getParent());
    if (Files.exists(up)) {
      Files.delete(up);
    }
    return up;
  }

  /**
   * Uploads a new dataset and submits a forced, high priority import request.
   *
   * @param zip if true zips up the data
   * @throws IllegalArgumentException if dataset was scheduled for importing already, queue was full or is currently being
   *         synced in the assembly
   *
   *         dataset does not exist or is not of matching origin
   */
  public ImportRequest upload(final int datasetKey, final InputStream content, boolean zip, @Nullable String filename, @Nullable String suffix, User user) throws IOException {
    validDataset(datasetKey);
    Path upload;
    if (filename == null) {
      filename = "upload-" + System.currentTimeMillis() + (suffix == null ? "" : "." + suffix);
    }
    upload = nCfg.scratchFile(datasetKey, filename).toPath();
    Files.createDirectories(upload.getParent());
    LOG.info("Upload data for dataset {} to tmp file {}", datasetKey, upload);
    Files.copy(content, upload, StandardCopyOption.REPLACE_EXISTING);

    if (zip) {
      Path uploadZip = createScratchUploadFile(datasetKey);
      LOG.debug("Zip uploaded file {} for dataset {} to {}", upload, datasetKey, uploadZip);
      CompressionUtil.zipFile(upload.toFile(), uploadZip.toFile());
      upload = uploadZip; // use zip for the final request object
    }
    return submitValidDataset(ImportRequest.upload(datasetKey, user.getKey(), upload));
  }

  public ImportRequest uploadXls(final int datasetKey, final InputStream content, User user) throws IOException {
    Preconditions.checkNotNull(content, "No content given");
    validDataset(datasetKey);
    // extract CSV files
    File csvDir = nCfg.scratchFile(datasetKey, "xls");
    if (csvDir.exists()) {
      FileUtils.deleteDirectory(csvDir);
    }
    csvDir.mkdirs();

    LOG.info("Extracting spreadsheet data for dataset {} to {}", datasetKey, csvDir);
    List<File> files = ExcelCsvExtractor.extract(content, csvDir);
    LOG.info("Extracted {} files from spreadsheet data for dataset {}", files.size(), datasetKey);
    // zip up as single source file for importer
    Path uploadZip = createScratchUploadFile(datasetKey);
    CompressionUtil.zipDir(csvDir, uploadZip.toFile());
    return submitValidDataset(ImportRequest.upload(datasetKey, user.getKey(), uploadZip));
  }

  /**
   * @throws IllegalArgumentException if dataset was scheduled for importing already, queue was full or is currently being
   *         synced in the assembly or dataset does not exist or is of origin managed
   */
  private synchronized ImportRequest submitValidDataset(final ImportRequest req) throws IllegalArgumentException {
    if (queueSize() >= iCfg.maxQueue) {
      LOG.info("Import queued at max {} already. Skip dataset {}", queueSize(), req.datasetKey);
      throw new IllegalArgumentException("Import queue full, skip dataset " + req.datasetKey);
    }

    var existing = importJob(req.datasetKey);
    if (existing.isPresent()) {
      // this dataset is already scheduled. Force a prio import?
      LOG.info("Dataset {} already queued for import", req.datasetKey);
      ImportJob job = existing.get();
      if (req.priority && job.isQueued()) {
        cancel(req.datasetKey, req.createdBy);
        LOG.info("Resubmit dataset {} for import with priority", req.datasetKey);
      } else {
        throw new IllegalArgumentException("Dataset " + req.datasetKey + " already queued for import");
      }
    }

    // is a sector from this dataset currently being synced?
    if (assemblyCoordinator != null) {
      DSID<Integer> sectorKey = assemblyCoordinator.hasSyncingSector(req.datasetKey);
      if (sectorKey != null) {
        LOG.warn("Dataset {} used in running sync of sector {}", req.datasetKey, sectorKey);
        throw new IllegalArgumentException("Dataset used in running sync of sector " + sectorKey);
      }
    }

    // this is a good guy, let it run!
    jobExecutor.submit(createImport(req));
    LOG.info("Queued import for dataset {}", req.datasetKey);
    return req;
  }

  private void validDataset(int datasetKey) {
    if (!hasStarted()) {
      throw UnavailableException.unavailable("dataset importer");
    }
    if (datasetKey == Datasets.COL) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is the CoL working draft and cannot be imported");
    }
    DaoUtils.requireOrigin(datasetKey, DatasetOrigin.EXTERNAL, "imported");
  }

  /**
   * @throws NotFoundException if dataset does not exist or was deleted
   * @throws IllegalArgumentException if dataset is of type managed
   */
  private ImportJob createImport(ImportRequest req) throws NotFoundException, IllegalArgumentException {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(req.datasetKey);
      if (d == null) {
        throw NotFoundException.notFound(Dataset.class, req.datasetKey);

      } else if (d.hasDeletedDate()) {
        LOG.warn("Dataset {} was deleted and cannot be imported", req.datasetKey);
        throw NotFoundException.notFound(Dataset.class, req.datasetKey);
      }
      DatasetSettings ds = dm.getSettings(req.datasetKey);
      // clear access URL if it's an upload
      // https://github.com/CatalogueOfLife/backend/issues/881
      if (req.hasUpload() && ds.has(Setting.DATA_ACCESS)) {
        ds.remove(Setting.DATA_ACCESS);
        dm.updateSettings(req.datasetKey, ds, req.createdBy);
      }
      return new ImportJob(req, new DatasetWithSettings(d, ds), iCfg, nCfg, dCfg, downloader, factory, importStoreFactory, index, validator, resolver, indexService, imgService, dao, dDao, sDao, decisionDao, bus,
        matcherFactory, scopeResolver, importTimer, failed
      );
    }
  }

  /**
   * Reschedules imports that were interrupted by the last server shutdown.
   * The job executor cancelled their stale job records on startup and keeps them for us.
   */
  private void rescheduleInterrupted() {
    List<ImportRequest> requests = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      var dim = session.getMapper(DatasetImportMapper.class);
      for (JobInfo stale : jobExecutor.getStaleJobs()) {
        // only reschedule import jobs, no releases or syncs
        if (!ImportJob.class.getSimpleName().equals(stale.getJob())) {
          continue;
        }
        DatasetImport di = dim.getByJobKey(stale.getKey());
        if (di != null) {
          try {
            requests.add(ImportRequest.reimport(di.getDatasetKey(), di.getAttempt(), di.getCreatedBy()));
          } catch (IllegalArgumentException e) {
            // swallow
          }
        }
      }
    }
    requests.forEach(this::submit);
    LOG.info("Resubmitted {} interrupted imports.", requests.size());
  }

  @Override
  public void start() throws Exception {
    LOG.info("Starting import manager with {} import threads and a queue of {} max.",
        iCfg.threads,
        iCfg.maxQueue);
    started = true;
    try {
      rescheduleInterrupted();
    } catch (RuntimeException e) {
      // log n swallow
      LOG.error("Error trying to reschedule older imports", e);
    }
  }

  @Override
  public void stop() throws Exception {
    // running and queued imports live in the shared job executor which interrupts them on its own shutdown
    started = false;
  }

  @Override
  public boolean hasStarted() {
    return started;
  }

  @Override
  public boolean isIdle() {
    return !hasStarted() || !hasRunning();
  }

  @Override
  public void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      LOG.debug("Try to cancel import job for deleted dataset {}. User={}", event.key, event.user);
      cancel(event.key, event.user);
    }
  }
}
