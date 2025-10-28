package life.catalogue.jobs;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.DatasetMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DatasetSchedulerJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetSchedulerJob.class);
  protected final SqlSessionFactory factory;
  private final JobExecutor exec;
  private final double threshold;
  private final DatasetOrigin[] origins;
  private DatasetMapper dm;

  /**
   * @param threshold the lowest percentage of records already processed that still triggers a reprocessing.
   *                  Can be zero or negative to process all incomplete datasets even if a single record is missing.
   * @param origins optional list of dataset origins to restrict the scheduler to
   */
  public DatasetSchedulerJob(int userKey, final double threshold, SqlSessionFactory factory, JobExecutor exec, DatasetOrigin...origins) {
    super(userKey);
    this.exec = exec;
    this.factory = factory;
    this.threshold = threshold;
    this.origins = origins;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return getClass() == other.getClass();
  }

  private static class DatasetMetrics {
    final int datasetKey;
    final int usages;
    final int done;

    public DatasetMetrics(int datasetKey, int usages, int done) {
      this.datasetKey = datasetKey;
      this.usages = usages;
      this.done = done;
    }

    public double percentage() {
      if (done == 0) return 0;
      if (usages == 0) return done;
      return ((double) done * 100 / (double) usages);
    }

    public boolean isComplete() {
      return done == usages;
    }

    public void write(Writer writer) throws IOException {
      writer.write(String.format("%6d %8d %8d %.2f%%%n", datasetKey, usages, done, percentage()));
    }
  }

  protected abstract int countDone(int datasetKey);

  protected void init(SqlSession session) {
    // nothing by default
  }

  protected abstract BackgroundJob buildJob(int datasetKey);

  private void processDatasets(ThrowingConsumer<DatasetMetrics, IOException> consumer) {
    // load dataset keys to rematch if there are no or less matches below the threshold
    try (SqlSession session = factory.openSession()) {
      this.dm = session.getMapper(DatasetMapper.class);
      init(session);
      for (int key : dm.keys(false, origins)) {
        var usages = countToBeDone(key);
        var done = countDone(key);
        consumer.accept(new DatasetMetrics(key, usages, done));
      }
    }
  }

  protected int countToBeDone(int datasetKey) {
    return dm.usageCount(datasetKey);
  }

  public void write(Writer writer) throws IOException {
    writer.write("   key   usages  matches    %\n");
    processDatasets( d -> d.write(writer));
  }

  @Override
  public void execute() {
    LOG.info("Schedule datasets for reprocessing. Triggered by {}", getUserKey());
    // load dataset keys to check if they need to be reprocessed
    AtomicInteger counter = new AtomicInteger();
    processDatasets( d -> {
      if ( (threshold <= 0 && !d.isComplete())  ||
           (threshold > 0 && (d.done == 0 || d.percentage() < threshold))
      ) {
        var job = buildJob(d.datasetKey);
        exec.submit(job);
        counter.incrementAndGet();
      }
    });
    LOG.info("Scheduled {} datasets.", counter);
  }
}