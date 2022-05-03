package life.catalogue.cache;

import java.util.Objects;

import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface LatestDatasetKeyCache {
  void setSqlSessionFactory(SqlSessionFactory factory);

  @Nullable Integer getLatestRelease(@NonNull Integer key);

  @Nullable Integer getLatestReleaseCandidate(@NonNull Integer key);

  @Nullable Integer getReleaseByAttempt(int projectKey, int attempt);

  @Nullable Integer getColAnnualRelease(int year);

  boolean isLatestRelease(int datasetKey);

  void refresh(int projectKey);

  class ReleaseAttempt{
    final int projectKey;
    final int attempt;

    public ReleaseAttempt(int projectKey, int attempt) {
      this.projectKey = projectKey;
      this.attempt = attempt;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ReleaseAttempt that = (ReleaseAttempt) o;
      return projectKey == that.projectKey && attempt == that.attempt;
    }

    @Override
    public int hashCode() {
      return Objects.hash(projectKey, attempt);
    }
  }

}
