package life.catalogue.matching;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;

import life.catalogue.api.model.DSID;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import life.catalogue.db.mapper.DatasetMapper;

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
  private final EventBus bus;
  @JsonProperty
  private final boolean missingOnly;

  @JsonProperty
  private final int[] datasetKeys;
  @JsonProperty
  private final List<? extends DSID<Integer>> sectorKeys;

  public static RematchJob one(int userKey, SqlSessionFactory factory, NameIndex ni, EventBus bus, boolean missingOnly, int datasetKey){
    return new RematchJob(userKey, factory, ni, bus, missingOnly, datasetKey);
  }

  public static RematchJob some(int userKey, SqlSessionFactory factory, NameIndex ni, EventBus bus, boolean missingOnly, int... datasetKeys){
    return new RematchJob(userKey, factory, ni, bus, missingOnly, datasetKeys);
  }

  public static RematchJob sector(int userKey, SqlSessionFactory factory, NameIndex ni, List<? extends DSID<Integer>> sectorKeys){
    return new RematchJob(userKey, factory, ni, sectorKeys, null);
  }

  private RematchJob(int userKey, SqlSessionFactory factory, NameIndex ni, List<? extends DSID<Integer>> sectorKeys, EventBus bus) {
    super(userKey);
    this.bus = bus;
    this.datasetKeys = null;
    this.sectorKeys = Preconditions.checkNotNull(sectorKeys);
    this.factory = factory;
    this.ni = ni.assertOnline();
    this.logToFile = true;
    this.missingOnly = false; // not supported for sectors
  }

  private RematchJob(int userKey, SqlSessionFactory factory, NameIndex ni, EventBus bus, boolean missingOnly, int... datasetKeys) {
    super(userKey);
    this.bus = bus;
    this.datasetKeys = Preconditions.checkNotNull(datasetKeys);
    this.sectorKeys = null;
    this.factory = factory;
    this.ni = ni.assertOnline();
    this.missingOnly = missingOnly;
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof RematchJob) {
      RematchJob job = (RematchJob) other;
      return Arrays.equals(datasetKeys, job.datasetKeys)
        && Objects.equals(sectorKeys, job.sectorKeys)
        && missingOnly == job.missingOnly;
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
    LOG.info("Rematching {}{} datasets with data. Triggered by {}", missingOnly ? "missing names from ":"", datasetKeys.length, getUserKey());

    DatasetMatcher matcher = new DatasetMatcher(factory, ni, bus);
    for (int key : datasetKeys) {
      matcher.match(key, true, missingOnly);
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