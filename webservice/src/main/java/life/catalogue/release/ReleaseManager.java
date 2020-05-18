package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ReleaseManager {
  private static final Logger LOG = LoggerFactory.getLogger(ReleaseManager.class);
  private static final ThreadPoolExecutor RELEASE_EXEC = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
      new ArrayBlockingQueue(1), new NamedThreadFactory("col-release"), new ThreadPoolExecutor.DiscardPolicy());

  // we only allow a single release to run at a time
  private static boolean LOCK = false;

  private final AcExporter exporter;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private final SqlSessionFactory factory;

  private ProjectRunnable job;

  public ReleaseManager(AcExporter exporter, DatasetImportDao diDao, NameUsageIndexService indexService, SqlSessionFactory factory) {
    this.exporter = exporter;
    this.diDao = diDao;
    this.indexService = indexService;
    this.factory = factory;
  }

  public Integer release(int datasetKey, User user) {
    return execute(() -> release(factory, indexService, exporter, diDao, datasetKey, user.getKey()));
  }

  public Integer duplicate(int datasetKey, User user) {
    return execute(() -> duplicate(factory, indexService, diDao, datasetKey, user.getKey()));
  }

  private Integer execute(Supplier<ProjectRunnable> jobSupplier) {
    if (job != null) {
      throw new IllegalStateException(job.getClass().getSimpleName() + " "+ job.getDatasetKey() + " to " + job.getNewDatasetKey() + " is already running");
    }

    job = jobSupplier.get();
    final int key = job.getNewDatasetKey();

    CompletableFuture.runAsync(job, RELEASE_EXEC)
      .exceptionally(ex -> {
        LOG.error("Failed to run {} on dataset {} to dataset {}", job.getClass().getSimpleName(), job.getDatasetKey(), job.getNewDatasetKey(), ex);
        return null;
      })
      .thenApply(x -> {
        // clear release reference when job is done
        job = null;
        return x;
      });
    return key;
  }

  public Optional<DatasetImport> getMetrics() {
    return job == null ? Optional.empty() : Optional.of(job.getMetrics());
  }


  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public static ProjectRelease release(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int projectKey, int userKey) {
    Dataset release = createDataset(factory, projectKey, userKey, ProjectRelease::releaseInit);
    return new ProjectRelease(factory, indexService, exporter, diDao, projectKey, release, userKey);
  }

  /**
   * Creates a duplicate of a managed project
   * @param projectKey the managed dataset to be copied
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public static ProjectDuplication duplicate(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, int projectKey, int userKey) {
    Dataset copy = createDataset(factory, projectKey, userKey, (d, ds) -> {
      d.setTitle(d.getTitle() + " copy");
    });
    return new ProjectDuplication(factory, indexService, diDao, projectKey, copy, userKey);
  }

  private static Dataset createDataset(SqlSessionFactory factory, int projectKey, int userKey, BiConsumer<Dataset, DatasetSettings> modifier) {
    if (!aquireLock()) {
      throw new IllegalStateException("There is a running release already");
    }

    Dataset copy;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      // create new dataset based on current metadata
      copy = dm.get(projectKey);
      if (copy.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Only managed datasets can be duplicated, but origin is " + copy.getOrigin());
      }

      copy.setKey(null);
      copy.setSourceKey(projectKey);
      copy.setOrigin(DatasetOrigin.RELEASED);
      copy.setAlias(null);
      copy.setModifiedBy(userKey);
      copy.setCreatedBy(userKey);

      if (modifier != null) {
        DatasetSettings ds = dm.getSettings(projectKey);
        modifier.accept(copy, ds);
      }
      dm.create(copy);

      return copy;

    } catch (Exception e) {
      LOG.error("Error creating new dataset for project {}", projectKey, e);
      releaseLock();
      throw new RuntimeException(e);
    }
  }

  private static synchronized boolean aquireLock(){
    if (!LOCK) {
      LOCK = true;
      return true;
    }
    return false;
  }

  static synchronized void releaseLock(){
    LOCK = false;
  }

}
