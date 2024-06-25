package life.catalogue.cache;

import java.util.UUID;

import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface LatestDatasetKeyCache {
  void setSqlSessionFactory(SqlSessionFactory factory);

  @Nullable Integer getLatestRelease(int projectKey, boolean extended);

  @Nullable Integer getLatestReleaseCandidate(int projectKey, boolean extended);

  @Nullable Integer getReleaseByAttempt(int projectKey, int attempt);

  default @Nullable Integer getColRelease(int year, boolean extended) {
    return getColRelease(year, 0, extended);
  }

  /**
   *
   * @param year 2 digits only after 2000, e.g. 24 for 2024
   * @param month 0 for annual release
   * @param extended
   * @return
   */
  @Nullable Integer getColRelease(int year, int month, boolean extended);

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
      public @Nullable Integer getColRelease(int year, int month, boolean extended) {
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
