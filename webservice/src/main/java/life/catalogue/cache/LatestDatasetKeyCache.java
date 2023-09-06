package life.catalogue.cache;

import java.util.Objects;
import java.util.UUID;

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

}
