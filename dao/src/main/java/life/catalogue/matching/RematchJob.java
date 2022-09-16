package life.catalogue.matching;

import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;

import java.util.Arrays;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.ints.IntSet;

public class RematchJob extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(RematchJob.class);
  private final SqlSessionFactory factory;
  private final NameIndex ni;

  @JsonProperty
  private final int[] datasetKeys;

  public static RematchJob all(int userKey, SqlSessionFactory factory, NameIndex ni){
    LOG.warn("Rematch all datasets with data using a names index of size {}", ni.size());
    // load dataset keys to rematch
    try (SqlSession session = factory.openSession(true)) {
      IntSet keys = DaoUtils.listDatasetWithNames(session);
      keys.addAll(
        session.getMapper(ArchivedNameUsageMapper.class).listProjects()
      );
      return new RematchJob(userKey, factory, ni, keys.toIntArray());
    }
  }

  public static RematchJob one(int userKey, SqlSessionFactory factory, NameIndex ni, int datasetKey){
    return new RematchJob(userKey, factory, ni, datasetKey);
  }

  public static RematchJob some(int userKey, SqlSessionFactory factory, NameIndex ni, int... datasetKeys){
    return new RematchJob(userKey, factory, ni, datasetKeys);
  }

  private RematchJob(int userKey, SqlSessionFactory factory, NameIndex ni, int... datasetKeys) {
    super(userKey);
    this.datasetKeys = Preconditions.checkNotNull(datasetKeys);
    this.factory = factory;
    this.ni = ni.assertOnline();
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

    DatasetMatcher matcher = new DatasetMatcher(factory, ni);
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