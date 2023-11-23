package life.catalogue.matching;

import life.catalogue.api.model.DSID;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
  @JsonProperty
  private final List<? extends DSID<Integer>> sectorKeys;

  public static RematchJob all(int userKey, SqlSessionFactory factory, NameIndex ni){
    // load dataset keys to rematch
    IntSet keys;
    try (SqlSession session = factory.openSession(true)) {
      keys = DaoUtils.listDatasetWithNames(session);
      keys.addAll(
        session.getMapper(ArchivedNameUsageMapper.class).listProjects()
      );
    }
    LOG.warn("Rematch all {} datasets with data using a names index of size {}", keys.size(), ni.size());
    return new RematchJob(userKey, factory, ni, keys.toIntArray());
  }

  public static RematchJob one(int userKey, SqlSessionFactory factory, NameIndex ni, int datasetKey){
    return new RematchJob(userKey, factory, ni, datasetKey);
  }

  public static RematchJob some(int userKey, SqlSessionFactory factory, NameIndex ni, int... datasetKeys){
    return new RematchJob(userKey, factory, ni, datasetKeys);
  }

  public static RematchJob sector(int userKey, SqlSessionFactory factory, NameIndex ni, List<? extends DSID<Integer>> sectorKeys){
    return new RematchJob(userKey, factory, ni, sectorKeys);
  }

  private RematchJob(int userKey, SqlSessionFactory factory, NameIndex ni, List<? extends DSID<Integer>> sectorKeys) {
    super(userKey);
    this.datasetKeys = null;
    this.sectorKeys = Preconditions.checkNotNull(sectorKeys);
    this.factory = factory;
    this.ni = ni.assertOnline();
  }

  private RematchJob(int userKey, SqlSessionFactory factory, NameIndex ni, int... datasetKeys) {
    super(userKey);
    this.datasetKeys = Preconditions.checkNotNull(datasetKeys);
    this.sectorKeys = null;
    this.factory = factory;
    this.ni = ni.assertOnline();
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof RematchJob) {
      RematchJob job = (RematchJob) other;
      return Arrays.equals(datasetKeys, job.datasetKeys) && Objects.equals(sectorKeys, job.sectorKeys);
    }
    return false;
  }

  @Override
  public void execute() {
    if (datasetKeys != null) {
      matchDatasets();
    } else {
      matchSectors();
    }
  }

  void matchDatasets() {
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

  void matchSectors() {
    LOG.info("Rematching {} sectors. Triggered by {}", sectorKeys.size(), getUserKey());

    SectorMatcher matcher = new SectorMatcher(factory, ni);
    for (var key : sectorKeys) {
      matcher.match(key, true);
    }

    LOG.info("Rematched {} sectors ({} failed), updating {} names from {} in total",
      matcher.getSectors(),
      sectorKeys.size() - matcher.getSectors(),
      matcher.getUpdated(),
      matcher.getTotal()
    );
  }
}