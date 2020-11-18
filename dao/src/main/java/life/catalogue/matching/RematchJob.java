package life.catalogue.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.IntPredicate;

public class RematchJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(RematchJob.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;

  @JsonProperty
  private final int[] datasetKeys;

  public static RematchJob all(User user, SqlSessionFactory factory, NameIndex ni){
    LOG.warn("Rematch all datasets with data using the existing names index");
    // load dataset keys to rematch
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
      IntSet keys = new IntOpenHashSet(dm.keys());
      keys.remove(Datasets.COL);
      keys.removeIf((IntPredicate) key -> !dpm.exists(key));
      // make sure COL gets processed first so we get low keys for the higher ranks in COL
      return new RematchJob(user, factory, ni, ArrayUtils.insert(0, keys.toIntArray(), Datasets.COL));
    }
  }

  public static RematchJob one(User user, SqlSessionFactory factory, NameIndex ni, int datasetKey){
    return new RematchJob(user, factory, ni, datasetKey);
  }

  public static RematchJob some(User user, SqlSessionFactory factory, NameIndex ni, int... datasetKeys){
    return new RematchJob(user, factory, ni, datasetKeys);
  }

  private RematchJob(User user, SqlSessionFactory factory, NameIndex ni, int... datasetKeys) {
    super(user.getKey());
    this.datasetKeys = Preconditions.checkNotNull(datasetKeys);
    this.factory = factory;
    this.ni = ni;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof RematchJob) {
      RematchJob job = (RematchJob) other;
      return Arrays.equals(datasetKeys, job.datasetKeys);
    }
    return false;
  }

  @Override
  public void execute() {
    LOG.info("Rematching {} datasets with data. Triggered by {}", datasetKeys.length, getUserKey());

    DatasetMatcher matcher = new DatasetMatcher(factory, ni, true);
    for (int key : datasetKeys) {
      matcher.match(key, true);
    }

    LOG.info("Rematched {} datasets ({} failed), updating {} names from {} in total",
      matcher.getDatasets(),
      datasetKeys.length - matcher.getDatasets(),
      matcher.getUpdated(),
      matcher.getTotal()
    );
  }
}