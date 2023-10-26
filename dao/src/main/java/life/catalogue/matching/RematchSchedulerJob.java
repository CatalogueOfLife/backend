package life.catalogue.matching;

import life.catalogue.common.func.ThrowingConsumer;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class RematchSchedulerJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(RematchSchedulerJob.class);
  private final Consumer<RematchJob> consumer;
  private final SqlSessionFactory factory;
  private final NameIndex ni;
  private final double threshold;

  public RematchSchedulerJob(int userKey, double threshold, SqlSessionFactory factory, NameIndex ni, Consumer<RematchJob> consumer) {
    super(userKey);
    this.consumer = consumer;
    this.factory = factory;
    this.ni = ni;
    this.threshold = threshold;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    return other instanceof RematchSchedulerJob;
  }

  private static class MatchMetrics {
    final int datasetKey;
    final int usages;
    final int matches;

    public MatchMetrics(int datasetKey, int usages, int matches) {
      this.datasetKey = datasetKey;
      this.usages = usages;
      this.matches = matches;
    }

    public double percentage() {
      return ( (double) matches / (double) usages);
    }

    public void write(Writer writer) throws IOException {
      writer.write(String.format("%6d %8d %8d %.2f%%%n", datasetKey, usages, matches, percentage()));
    }
  }

  private void processDatasets(ThrowingConsumer<MatchMetrics, IOException> consumer) {
    // load dataset keys to rematch if there are no or less matches below the threshold
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      var nmm = session.getMapper(NameMatchMapper.class);

      int counter = 0;
      int counterNone = 0;
      for (int key : dm.keys()) {
        counter++;
        var usages = dm.usageCount(key);
        if (!nmm.exists(key)) {
          counterNone++;
          consumer.accept(new MatchMetrics(key, usages, 0));
        } else {
          int matches = nmm.count(key);
          consumer.accept(new MatchMetrics(key, usages, matches));
        }
      }
      LOG.info("Processed {} datasets. {} of which had no matches at all.", counter, counterNone);
    }
  }

  public void write(Writer writer) throws IOException {
    writer.write("   key   usages  matches    %\n");
    processDatasets( d -> d.write(writer));
  }

  @Override
  public void execute() {
    LOG.info("Schedule rematching jobs for currently unmatched datasets. Triggered by {}", getUserKey());
    // load dataset keys to rematch if there are no or less matches below the threshold
    AtomicInteger counter = new AtomicInteger();
    processDatasets( d -> {
      if (d.matches == 0 || d.percentage() < threshold) {
        var job = RematchJob.one(getUserKey(), factory, ni, d.datasetKey);
        consumer.accept(job);
        counter.incrementAndGet();
      }
    });
    LOG.info("Scheduled {} datasets for matching.", counter);
  }
}