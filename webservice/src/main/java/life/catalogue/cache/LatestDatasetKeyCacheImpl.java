package life.catalogue.cache;

import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.NotFoundException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * Cache that listens to dataset changes and provides the latest dataset keys for project releases
 * and specific release attempts.
 */
public class LatestDatasetKeyCacheImpl implements LatestDatasetKeyCache {
  private static final Logger LOG = LoggerFactory.getLogger(LatestDatasetKeyCacheImpl.class);
  private final static int MAX_SIZE = 1000;
  private SqlSessionFactory factory;
  private final LoadingCache<Integer, Integer> latestRelease = Caffeine.newBuilder()
                                                                       .maximumSize(MAX_SIZE)
                                                                       .expireAfterWrite(1, TimeUnit.HOURS)
                                                                       .build(k -> lookupLatest(k, false, false));
  private final LoadingCache<Integer, Integer> latestXRelease = Caffeine.newBuilder()
                                                                        .maximumSize(MAX_SIZE)
                                                                        .expireAfterWrite(1, TimeUnit.HOURS)
                                                                        .build(k -> lookupLatest(k, false, true));
  private final LoadingCache<Integer, Integer> latestCandidate = Caffeine.newBuilder()
                                                                         .maximumSize(MAX_SIZE)
                                                                         .expireAfterWrite(1, TimeUnit.HOURS)
                                                                         .build(k -> lookupLatest(k, true, false));
  private final LoadingCache<Integer, Integer> latestXCandidate = Caffeine.newBuilder()
                                                                          .maximumSize(MAX_SIZE)
                                                                          .expireAfterWrite(1, TimeUnit.HOURS)
                                                                          .build(k -> lookupLatest(k, true, true));
  private final LoadingCache<Integer, Integer> annualReleases = Caffeine.newBuilder()
                                                                        .maximumSize(50)
                                                                        .expireAfterWrite(30, TimeUnit.DAYS)
                                                                        .build(y -> lookupAnnualRelease(y, false));
  private final LoadingCache<Integer, Integer> annualXReleases = Caffeine.newBuilder()
                                                                        .maximumSize(50)
                                                                        .expireAfterWrite(30, TimeUnit.DAYS)
                                                                         .build(y -> lookupAnnualRelease(y, true));
  private final LoadingCache<ReleaseAttempt, Integer> releaseAttempt = Caffeine.newBuilder()
                                                                               .maximumSize(MAX_SIZE)
                                                                               .expireAfterWrite(30, TimeUnit.DAYS)
                                                                               .build(this::lookupAttempt);


  public LatestDatasetKeyCacheImpl(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  @Override
  @Nullable
  public Integer getLatestRelease(int projectKey, boolean extended) {
    return extended ? latestXRelease.get(projectKey): latestRelease.get(projectKey);
  }

  @Override
  @Nullable
  public Integer getLatestReleaseCandidate(int projectKey, boolean extended) {
    return extended ? latestXCandidate.get(projectKey): latestCandidate.get(projectKey);
  }

  @Override
  @Nullable
  public Integer getReleaseByAttempt(int projectKey, int attempt) {
    return releaseAttempt.get(new ReleaseAttempt(projectKey, attempt));
  }

  @Override
  public @Nullable Integer getColAnnualRelease(int year, boolean extended) {
    return extended ? annualXReleases.get(year) : annualReleases.get(year);
  }

  @Override
  public boolean isLatestRelease(int datasetKey) {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.RELEASE && info.sourceKey != null) {
      return Objects.equals(getLatestRelease(info.sourceKey, false), datasetKey);
    }
    return false;
  }

  /**
   * @param projectKey a dataset key that is known to exist and point to a managed dataset
   * @return dataset key for the latest release of a project or null in case no release exists
   */
  private Integer lookupLatest(int projectKey, boolean candidate, boolean extended) throws NotFoundException {
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

  private Integer lookupAnnualRelease(int year, boolean extended) {
    final String col = extended ? "XCOL" : "COL";
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setAlias(String.format("%s%02d", col, year - 2000));
      req.setPrivat(false);
      if (year >= 2021 || extended) {
        // proper releases
        req.setReleasedFrom(Datasets.COL);
      } else {
        // external datasets
        req.setOrigin(List.of(DatasetOrigin.EXTERNAL));
      }
      var resp = dm.search(req, null, new Page());
      if (resp != null && !resp.isEmpty()) {
        if (resp.size() > 1) {
          LOG.warn("Multiple public {} releases found with alias {}", col, req.getAlias());
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
