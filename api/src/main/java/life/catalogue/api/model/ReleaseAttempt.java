package life.catalogue.api.model;

import java.util.Objects;

public class ReleaseAttempt {
  public final int projectKey;
  public final int attempt;

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
