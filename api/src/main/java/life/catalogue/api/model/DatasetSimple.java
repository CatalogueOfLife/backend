package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.date.FuzzyDate;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public class DatasetSimple {
  private Integer key;
  private Integer sourceKey;
  private DatasetOrigin origin;
  private String alias;
  private String title;
  private String version;
  private FuzzyDate issued;
  private boolean privat;
  private boolean deleted;
  private Integer attempt;
  private DOI doi;
  private URI url;
  private UUID gbifKey;
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
    issued = d.getIssued();
    privat = d.isPrivat();
    deleted = d.hasDeletedDate();
    attempt = d.getAttempt();
    doi = d.getDoi();
    url = d.getUrl();
    gbifKey = d.getGbifKey();
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

  public FuzzyDate getIssued() {
    return issued;
  }

  public void setIssued(FuzzyDate issued) {
    this.issued = issued;
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

  /**
   * @return the last successful import or release attempt that created the current data
   */
  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  /**
   * @return true if at least one import or release has succeeded before and therefore produced data
   */
  @JsonProperty(value = "hasData", access = JsonProperty.Access.READ_ONLY)
  public boolean hasData() {
    return attempt != null;
  }

  public DOI getDoi() {
    return doi;
  }

  public void setDoi(DOI doi) {
    this.doi = doi;
  }

  public URI getUrl() {
    return url;
  }

  public void setUrl(URI url) {
    this.url = url;
  }

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
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
      Objects.equals(issued, that.issued) &&
      Objects.equals(attempt, that.attempt) &&
      Objects.equals(doi, that.doi) &&
      Objects.equals(url, that.url) &&
      Objects.equals(gbifKey, that.gbifKey) &&
      Objects.equals(gbifPublisherKey, that.gbifPublisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, sourceKey, origin, alias, title, version, issued, privat, deleted, attempt, doi, url, gbifKey, gbifPublisherKey);
  }

  @Override
  public String toString() {
    return "DatasetSimple{" +
        "key=" + key +
        ", sourceKey=" + sourceKey +
        ", origin=" + origin +
        ", alias='" + alias + '\'' +
        ", title='" + title + '\'' +
        ", version='" + version + '\'' +
        ", issued=" + issued +
        ", privat=" + privat +
        ", deleted=" + deleted +
        ", attempt=" + attempt +
        ", doi=" + doi +
        ", url=" + url +
        ", gbifKey=" + gbifKey +
        ", gbifPublisherKey=" + gbifPublisherKey +
        '}';
  }
}
