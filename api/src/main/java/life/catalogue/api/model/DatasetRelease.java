package life.catalogue.api.model;

import com.google.common.base.Preconditions;

import life.catalogue.api.vocab.DatasetOrigin;

import java.util.Objects;

public class DatasetRelease {
  private int key;
  private int projectKey;
  private int attempt;
  private DatasetOrigin origin;
  private String alias;
  private String version;
  private boolean privat;
  private boolean deleted;

  public DatasetRelease() {
  }

  /**
   * Attempt is not set !!!
   */
  public DatasetRelease(Dataset d) {
    Preconditions.checkArgument(d.getOrigin().isRelease(), "Dataset %s is not a release", d.getKey());
    key = d.getKey();
    projectKey = d.getSourceKey();
    origin = d.getOrigin();
    alias = d.getAlias();
    version = d.getVersion();
    privat = d.isPrivat();
    deleted = d.hasDeletedDate();
  }

  public int getKey() {
    return key;
  }

  public void setKey(int key) {
    this.key = key;
  }

  public int getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(int projectKey) {
    this.projectKey = projectKey;
  }

  public int getAttempt() {
    return attempt;
  }

  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }

  public boolean isPrivat() {
    return privat;
  }

  public void setPrivat(boolean privat) {
    this.privat = privat;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * @return true for COL Annual releases, false otherwise
   */
  public boolean hasLongTermSupport() {
    return version != null && version.startsWith("Annual");
  }

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetRelease)) return false;
    DatasetRelease that = (DatasetRelease) o;
    return key == that.key &&
      projectKey == that.projectKey &&
      attempt == that.attempt &&
      privat == that.privat &&
      deleted == that.deleted &&
      origin == that.origin &&
      Objects.equals(alias, that.alias) &&
      Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, projectKey, attempt, origin, alias, version, privat, deleted);
  }
}
