package life.catalogue.importer;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.util.PagingUtil;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.assembly.AssemblyCoordinator;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.io.PathUtils;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.PBQThreadPoolExecutor;
import life.catalogue.csv.ExcelCsvExtractor;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.ManagedExtended;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndex;
import life.catalogue.metadata.DoiResolver;
import life.catalogue.release.AbstractProjectCopy;

import org.gbif.nameparser.utils.NamedThreadFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validator;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Manages import task scheduling, removing and listing
 */
public class ImportManager implements ManagedExtended {
  private static final Logger LOG = LoggerFactory.getLogger(ImportManager.class);
  public static final String THREAD_NAME = "dataset-importer";
  static final Comparator<DatasetImport> DI_STARTED_COMPARATOR = Comparator.comparing(DatasetImport::getStarted);

  private PBQThreadPoolExecutor<ImportJob> exec;
  private AssemblyCoordinator assemblyCoordinator;
  private final Map<Integer, PBQThreadPoolExecutor.ComparableFutureTask> futures = new ConcurrentHashMap<>();
  private final WsServerConfig cfg;
  private final DownloadUtil downloader;
  private final DoiResolver resolver;
  private final SqlSessionFactory factory;
  private final NameIndex index;
  private final NameUsageIndexService indexService;

  private final JobExecutor jobExecutor;

  private final SectorDao sDao;
  private final DatasetDao dDao;
  private final DatasetImportDao dao;
  private final DecisionDao decisionDao;
  private final ImageService imgService;
  private final Validator validator;
  private final Timer importTimer;
  private final Counter failed;

  public ImportManager(WsServerConfig cfg, MetricRegistry registry, CloseableHttpClient client,
      SqlSessionFactory factory, NameIndex index, DatasetDao dDao, SectorDao sDao, DecisionDao decisionDao,
      NameUsageIndexService indexService, ImageService imgService, JobExecutor jobExecutor, Validator validator, DoiResolver resolver) {
    this.cfg = cfg;
    this.factory = factory;
    this.validator = validator;
    this.resolver = resolver;
    this.jobExecutor = jobExecutor;
    this.downloader = new DownloadUtil(client, cfg.importer.githubToken, cfg.importer.githubTokenGeoff);
    this.index = index;
    this.imgService = imgService;
    this.indexService = indexService;
    this.dDao = dDao;
    this.sDao = sDao;
    this.decisionDao = decisionDao;
    this.dao = new DatasetImportDao(factory, cfg.metricsRepo);
    importTimer = registry.timer("life.catalogue.import.timer");
    failed = registry.counter("life.catalogue.import.failed");
  }

  public void setAssemblyCoordinator(AssemblyCoordinator assemblyCoordinator) {
    this.assemblyCoordinator = assemblyCoordinator;
  }

  /**
   * Lists the ImportRequests of the current queue
   */
  public List<ImportRequest> queue() {
    if (!hasStarted()) {
      return Collections.emptyList();
    }
    return exec.getQueue()
        .stream()
        .map(ImportJob::getRequest)
        .collect(Collectors.toList());
  }

  public int queueSize() {
    if (!hasStarted()) {
      return 0;
    }
    return exec.queueSize();
  }

  /**
   * Pages through all queued, running and historical imports. See https://github.com/Sp2000/colplus-backend/issues/404
   */
  public ResultPage<DatasetImport> listImports(Integer datasetKey, List<ImportState> states, Page page) {
    List<DatasetImport> running = running(datasetKey, states);
    ResultPage<DatasetImport> historical;

    // ignore running states in imports stored in the db - otherwise we get duplicates
    List<ImportState> historicalStates = states == null ? Collections.EMPTY_LIST
        : states.stream()
            .filter(ImportState::isFinished)
            .collect(Collectors.toList());

    if (states != null && !states.isEmpty() && historicalStates.isEmpty()) {
      // we originally had a request for only running states. We dont get any of these from the db
      historical = new ResultPage<>(new Page(0, 0), 0, Collections.EMPTY_LIST);

    } else {
      // query historical ones at least to get the total
      if (running.size() >= page.getLimitWithOffset()) {
        // we can answer the request from the queue alone, so limit=0 to get the total count!
        historical = dao.list(datasetKey, historicalStates, new Page(0, 0));

      } else {
        int offset = Math.max(0, page.getOffset() - running.size());
        int limit = Math.min(page.getLimit(), page.getLimitWithOffset() - running.size());
        historical = dao.list(datasetKey, historicalStates, new Page(offset, limit));
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

  private static DatasetImport fromFuture(PBQThreadPoolExecutor.ComparableFutureTask f) {
    return fromImportJob((ImportJob) f.getTask());
  }

  private static DatasetImport fromImportJob(ImportJob job) {
    DatasetImport di = job.getDatasetImport();
    if (di == null) {
      di = new DatasetImport();
      di.setDatasetKey(job.getDatasetKey());
      di.setAttempt(ObjectUtils.coalesce(job.getAttempt(), -1));
      di.setState(ImportState.WAITING);
    }
    return di;
  }

  private List<DatasetImport> running(final Integer datasetKey, final List<ImportState> states) {
    // make sure we have all running ones in and on top!
    List<DatasetImport> running = futures.values()
        .stream()
        .map(ImportManager::fromFuture)
        .filter(di -> di.getState().isRunning())
        .collect(Collectors.toList());

    // include releasing jobs if existing and sort by creation date
    for (AbstractProjectCopy projJob : jobExecutor.getQueue(AbstractProjectCopy.class)) {
      running.add(projJob.getMetrics());
    }
    running.sort(DI_STARTED_COMPARATOR);

    // then add the priority queue from the executor, filtered for queued imports only keeping the queues priority order
    if (hasStarted()) {
      running.addAll(
          exec.getQueue()
              .stream()
              .map(ImportManager::fromImportJob)
              .filter(di -> di.getState().isQueued())
              .collect(Collectors.toList()));
    }

    // finally filter by dataset & state
    return running.stream()
        .filter(di -> {
          if (datasetKey != null && !Objects.equals(datasetKey, di.getDatasetKey())) {
            return false;
          }
          if (states != null && di.getState() != null && !states.contains(di.getState())) {
            return false;
          }
          return true;
        })
        .collect(Collectors.toList());
  }

  /**
   * @return true if queue is empty
   */
  public boolean hasEmptyQueue() {
    return exec.hasEmptyQueue();
  }

  /**
   * @return true if imports are running or queued
   */
  public boolean hasRunning() {
    return !futures.isEmpty();
  }

  /**
   * @return true if import for given dataset is running or queued
   */
  public boolean isRunning(int datasetKey) {
    return futures.containsKey(datasetKey);
  }

  /**
   * Cancels a running import job by its dataset key
   */
  public void cancel(int datasetKey, int user) {
    Future f = futures.remove(datasetKey);
    if (f != null) {
      f.cancel(true);
      exec.purge();
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
    validRequest(req);
    return submitValidDataset(req);
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
    Dataset d = validDataset(datasetKey);
    uploadArchive(d, content, zip, filename, suffix);
    return submitValidDataset(ImportRequest.upload(datasetKey, user.getKey()));
  }

  public ImportRequest uploadXls(final int datasetKey, final InputStream content, User user) throws IOException {
    Preconditions.checkNotNull(content, "No content given");
    Dataset d = validDataset(datasetKey);
    // extract CSV files
    File tmpDir = cfg.normalizer.scratchFile(datasetKey, "xls");
    tmpDir.mkdirs();

    LOG.info("Extracting spreadsheet data for dataset {} to {}", d.getKey(), tmpDir);
    List<File> files = ExcelCsvExtractor.extract(content, tmpDir);
    LOG.info("Extracted {} files from spreadsheet data for dataset {}", files.size(), d.getKey());
    // zip up as single source file for importer
    Path source = cfg.normalizer.source(d.getKey()).toPath();
    Files.createDirectories(source.getParent());
    if (Files.exists(source)) {
      Files.delete(source);
    }
    CompressionUtil.zipDir(tmpDir, source.toFile());
    return submitValidDataset(ImportRequest.upload(datasetKey, user.getKey()));
  }

  /**
   * @throws IllegalArgumentException if dataset was scheduled for importing already, queue was full or is currently being
   *         synced in the assembly or dataset does not exist or is of origin managed
   */
  private synchronized ImportRequest submitValidDataset(final ImportRequest req) throws IllegalArgumentException {
    if (exec.queueSize() >= cfg.importer.maxQueue) {
      LOG.info("Import queued at max {} already. Skip dataset {}", exec.queueSize(), req.datasetKey);
      throw new IllegalArgumentException("Import queue full, skip dataset " + req.datasetKey);

    } else if (futures.containsKey(req.datasetKey)) {
      // this dataset is already scheduled. Force a prio import?
      LOG.info("Dataset {} already queued for import", req.datasetKey);
      PBQThreadPoolExecutor.ComparableFutureTask f = futures.get(req.datasetKey);
      if (req.priority && exec.isQueued(f)) {
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
    futures.put(req.datasetKey, exec.submit(createImport(req), req.priority));
    LOG.info("Queued import for dataset {}", req.datasetKey);
    return req;
  }


  private Dataset validRequest(ImportRequest request) {
    Dataset d = validDataset(request.datasetKey);
    // does a local archive exist for uploads?
    if (request.upload) {
      File f = cfg.normalizer.source(request.datasetKey);
      if (!f.exists()) {
        throw new IllegalArgumentException("No local archive exists for dataset " + request.datasetKey);
      }
    }
    return d;
  }

  private Dataset validDataset(int datasetKey) {
    if (!hasStarted()) {
      throw UnavailableException.unavailable("dataset importer");
    }
    if (datasetKey == Datasets.COL) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is the CoL working draft and cannot be imported");
    }
    try (SqlSession session = factory.openSession(true)) {
      return DaoUtils.assertMutable(datasetKey, "imported", session);
    }
  }

  /**
   * We use old school callbacks here as you cannot easily cancel CopletableFutures.
   */
  private void successCallBack(ImportRequest req) {
    Duration durQueued = Duration.between(req.created, req.started);
    Duration durRun = Duration.between(req.started, LocalDateTime.now());
    LOG.info("Dataset import {} finished. {} min queued, {} min to execute", req.datasetKey, durQueued.toMinutes(), durRun.toMinutes());
    importTimer.update(durRun.getSeconds(), TimeUnit.SECONDS);
    futures.remove(req.datasetKey);
  }

  /**
   * We use old school callbacks here as you cannot easily cancel CompletableFutures.
   */
  private void errorCallBack(ImportRequest req, Exception err) {
    futures.remove(req.datasetKey);
    failed.inc();
    LOG.error("Dataset import {} failed: {}", req.datasetKey, Exceptions.getFirstMessage(err), err.getCause());
  }

  /**
   * Uploads an input stream to a tmp file and if no errors moves it to the archive source path.
   */
  private void uploadArchive(Dataset d, InputStream content, boolean zip, @Nullable String filename, @Nullable String suffix) throws NotFoundException, IOException {
    Path tmp;
    Path tmpDir = cfg.normalizer.scratchDir.toPath().resolve(d.getKey().toString());
    Files.createDirectories(tmpDir);
    if (filename == null) {
      tmp = Files.createTempFile(tmpDir, "upload-", Strings.nullToEmpty("."+suffix));
    } else {
      tmp = tmpDir.resolve(filename);
    }
    LOG.info("Upload data for dataset {} to tmp file {}", d.getKey(), tmp);
    Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);

    Path source = cfg.normalizer.source(d.getKey()).toPath();
    Files.createDirectories(source.getParent());
    if (zip) {
      if (Files.exists(source)) {
        Files.delete(source);
      }
      LOG.debug("Zip uploaded file {} for dataset {} to source repo at {}", tmp, d.getKey(), source);
      CompressionUtil.zipFile(tmp.toFile(), source.toFile());
    } else {
      LOG.debug("Move uploaded data for dataset {} to source repo at {}", d.getKey(), source);
      Files.move(tmp, source, StandardCopyOption.REPLACE_EXISTING);
    }
    // remove tmp folder
    PathUtils.deleteRecursively(tmpDir);
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
      // clear access URL if its an upload
      // https://github.com/CatalogueOfLife/backend/issues/881
      if (req.upload && ds.has(Setting.DATA_ACCESS)) {
        ds.remove(Setting.DATA_ACCESS);
        dm.updateSettings(req.datasetKey, ds, req.createdBy);
      }
      return new ImportJob(req, new DatasetWithSettings(d, ds), cfg, downloader, factory, index, validator, resolver, indexService, imgService, dDao, sDao, decisionDao,
        () -> req.start(),
          this::successCallBack,
          this::errorCallBack);
    }
  }

  @Override
  public void start() {
    LOG.info("Starting import manager with {} import threads and a queue of {} max.",
        cfg.importer.threads,
        cfg.importer.maxQueue);

    exec = new PBQThreadPoolExecutor<>(cfg.importer.threads,
        60L,
        TimeUnit.SECONDS,
        new PriorityBlockingQueue<>(cfg.importer.maxQueue),
        new NamedThreadFactory(THREAD_NAME, Thread.NORM_PRIORITY, true),
        new ThreadPoolExecutor.AbortPolicy());
    cancelAndReschedule();
  }

  /**
   * Read hanging imports in db, truncate if half inserted and add as new requests to the queue
   */
  private void cancelAndReschedule() {
    List<ImportRequest> requests = new ArrayList<>();
    Iterator<DatasetImport> iter = PagingUtil.pageAll(p -> dao.list(null, ImportState.runningAndWaitingStates(), p));
    while (iter.hasNext()) {
      DatasetImport di = iter.next();
      // only reschedule import jobs, no releases
      if (!di.getJob().equalsIgnoreCase(ImportJob.class.getSimpleName())) {
        continue;
      }
      // mark as cancelled
      dao.updateImportCancelled(di);
      // add back to queue
      try {
        requests.add(new ImportRequest(di.getDatasetKey(), di.getCreatedBy(), true, false, di.isUpload()));
      } catch (IllegalArgumentException e) {
        // swallow
      }
    }
    // finally submit all request. We don't do this earlier to not disturb the paging which would yield the newly scheduled imports again and cancel them
    requests.forEach(this::submit);
    LOG.info("Cancelled and resubmitted {} imports.", requests.size());
  }

  @Override
  public void stop() {
    // orderly shutdown running imports
    for (Future f : futures.values()) {
      f.cancel(true);
    }
    // fully shutdown threadpool within given time
    if (exec != null) {
      exec.stop();
      exec = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return exec != null;
  }

  public boolean restart() {
    LOG.info("Restarting dataset importer");
    try {
      stop();
      start();
      return true;

    } catch (Exception e) {
      LOG.error("Failed to restart importer", e);
      return false;
    }
  }
}
