package life.catalogue.cache;

import java.util.UUID;

import life.catalogue.es.NameUsageIndexService;

import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface LatestDatasetKeyCache {
  void setSqlSessionFactory(SqlSessionFactory factory);

  @Nullable Integer getLatestRelease(int projectKey, boolean extended);

  @Nullable Integer getLatestReleaseCandidate(int projectKey, boolean extended);

  @Nullable Integer getReleaseByAttempt(int projectKey, int attempt);

  @Nullable Integer getColAnnualRelease(int year, boolean extended);

  @Nullable Integer getDatasetKeyByGbif(UUID gbif);

  boolean isLatestRelease(int datasetKey);

  void refresh(int projectKey);

  static LatestDatasetKeyCache passThru() {
    return new LatestDatasetKeyCache() {
      @Override
      public void setSqlSessionFactory(SqlSessionFactory factory) {

      }

      @Override
      public @Nullable Integer getLatestRelease(int projectKey, boolean extended) {
        return null;
      }

      @Override
      public @Nullable Integer getLatestReleaseCandidate(int projectKey, boolean extended) {
        return null;
      }

      @Override
      public @Nullable Integer getReleaseByAttempt(int projectKey, int attempt) {
        return null;
      }

      @Override
      public @Nullable Integer getColAnnualRelease(int year, boolean extended) {
        return null;
      }

      @Override
      public @Nullable Integer getDatasetKeyByGbif(UUID gbif) {
        return null;
      }

      @Override
      public boolean isLatestRelease(int datasetKey) {
        return false;
      }

      @Override
      public void refresh(int projectKey) {

      }
    };
  }

  }
