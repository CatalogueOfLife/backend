package life.catalogue.cache;

import com.esotericsoftware.minlog.Log;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.NotFoundException;

import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.checkerframework.checker.units.qual.K;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache that listens to dataset changes and provides the latest dataset keys for project releases
 * and specific release attempts.
 */
public class LatestDatasetKeyCacheImpl implements LatestDatasetKeyCache {
  private static final Logger LOG = LoggerFactory.getLogger(LatestDatasetKeyCacheImpl.class);

  private SqlSessionFactory factory;
  private final LoadingCache<Integer, Integer> latestRelease = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build(k -> lookupLatest(k, false));
  private final LoadingCache<Integer, Integer> latestCandidate = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build(k -> lookupLatest(k, true));
  private final LoadingCache<ReleaseAttempt, Integer> releaseAttempt = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(30, TimeUnit.DAYS)
    .build(this::lookupAttempt);
  private final LoadingCache<Integer, Integer> annualReleases = Caffeine.newBuilder()
    .maximumSize(50)
    .expireAfterWrite(30, TimeUnit.DAYS)
    .build(this::lookupAnnualRelease);


  public LatestDatasetKeyCacheImpl(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  @Nullable
  public Integer getLatestRelease(@NonNull Integer key) {
    return latestRelease.get(key);
  }

  @Override
  @Nullable
  public Integer getLatestReleaseCandidate(@NonNull Integer key) {
    return latestCandidate.get(key);
  }

  @Override
  @Nullable
  public Integer getReleaseByAttempt(int projectKey, int attempt) {
    return releaseAttempt.get(new ReleaseAttempt(projectKey, attempt));
  }

  @Override
  public @Nullable Integer getColAnnualRelease(int year) {
    return annualReleases.get(year);
  }

  @Override
  public boolean isLatestRelease(int datasetKey) {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.RELEASED && info.sourceKey != null) {
      return Objects.equals(getLatestRelease(info.sourceKey), datasetKey);
    }
    return false;
  }

  /**
   * @param projectKey a dataset key that is known to exist and point to a managed dataset
   * @return dataset key for the latest release of a project or null in case no release exists
   */
  private Integer lookupLatest(int projectKey, boolean candidate) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.latestRelease(projectKey, !candidate);
    }
  }

  private Integer lookupAttempt(ReleaseAttempt release) throws NotFoundException {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.releaseAttempt(release.projectKey, release.attempt);
    }
  }

  private Integer lookupAnnualRelease(int year) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setReleasedFrom(Datasets.COL);
      req.setAlias(String.format("COL%02d", year-2000));
      var resp = dm.search(req,null,new Page());
      if (resp != null && !resp.isEmpty()) {
        if (resp.size() > 1) {
          LOG.warn("Multiple public COL releases found with alias {}", req.getAlias());
        }
        return resp.get(0).getKey();
      }
      return null;
    }
  }

  /**
   * Refreshes the latest release(candidate) cache for a given project
   */
  @Override
  public void refresh(int projectKey) {
    latestRelease.refresh(projectKey);
    latestCandidate.refresh(projectKey);
  }

}
