package life.catalogue.matching;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;

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

  private void rematch(int key) {
    var job = RematchJob.one(getUserKey(), factory, ni, key);
    consumer.accept(job);
  }

  @Override
  public void execute() {
    LOG.info("Schedule rematching jobs for currently unmatched datasets. Triggered by {}", getUserKey());
    // load dataset keys to rematch if there are no or less matches below the threshold
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      var um = session.getMapper(NameUsageMapper.class);
      var nmm = session.getMapper(NameMatchMapper.class);

      int counter = 0;
      IntSet withMatches = new IntOpenHashSet();
      for (int key : dm.keys()) {
        if (!nmm.exists(key)) {
          counter++;
          rematch(key);
        } else {
          withMatches.add(key);
        }
      }
      LOG.info("Scheduled {} datasets with no matches. Look now also for datasets with few matches only", counter);

      int counter2 = 0;
      for (int key : withMatches) {
        int total = um.count(key);
        int matches = nmm.count(key);
        if (((double) matches / (double) total) < threshold) {
          counter2++;
          rematch(key);
        }
      }
      LOG.info("Scheduled {} datasets with few matches.", counter2);
    }
  }
}