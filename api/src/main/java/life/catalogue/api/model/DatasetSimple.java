package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.api.vocab.DatasetOrigin;

import java.util.Objects;
import java.util.UUID;

public class DatasetSimple {
  private Integer key;
  private Integer sourceKey;
  private DatasetOrigin origin;
  private String alias;
  private String title;
  private String version;
  private boolean privat;
  private boolean deleted;
  private UUID gbifPublisherKey;

  public DatasetSimple() {
  }

  public DatasetSimple(Dataset d) {
    key = d.getKey();
    sourceKey = d.getSourceKey();
    origin = d.getOrigin();
    alias = d.getAlias();
    title = d.getTitle();
    version = d.getVersion();
    privat = d.isPrivat();
    deleted = d.hasDeletedDate();
    gbifPublisherKey = d.getGbifPublisherKey();
  }

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public Integer getSourceKey() {
    return sourceKey;
  }

  public void setSourceKey(Integer sourceKey) {
    this.sourceKey = sourceKey;
  }

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  @JsonProperty("private")
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

  public UUID getGbifPublisherKey() {
    return gbifPublisherKey;
  }

  public void setGbifPublisherKey(UUID gbifPublisherKey) {
    this.gbifPublisherKey = gbifPublisherKey;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetSimple)) return false;

    DatasetSimple that = (DatasetSimple) o;
    return privat == that.privat &&
      deleted == that.deleted &&
      Objects.equals(key, that.key) &&
      Objects.equals(sourceKey, that.sourceKey) &&
      origin == that.origin &&
      Objects.equals(alias, that.alias) &&
      Objects.equals(title, that.title) &&
      Objects.equals(version, that.version) &&
      Objects.equals(gbifPublisherKey, that.gbifPublisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, sourceKey, origin, alias, title, version, privat, deleted, gbifPublisherKey);
  }
}
