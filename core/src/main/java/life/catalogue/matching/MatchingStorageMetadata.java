package life.catalogue.matching;

import life.catalogue.api.model.Dataset;

import java.time.LocalDate;
import java.util.Objects;

public class MatchingStorageMetadata {
  private int datasetKey;
  private String alias;
  private String title;
  private String version;
  private int numUsages;
  private int numCanonicals;
  private int numNidx;
  private String created;

  public MatchingStorageMetadata() {
  }

  public MatchingStorageMetadata(Dataset d) {
    datasetKey = d.getKey();
    alias = d.getAlias();
    title = d.getTitle();
    version = d.getVersion();
    created = LocalDate.now().toString();
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
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

  public int getNumUsages() {
    return numUsages;
  }

  public void setNumUsages(int numUsages) {
    this.numUsages = numUsages;
  }

  public int getNumCanonicals() {
    return numCanonicals;
  }

  public void setNumCanonicals(int numCanonicals) {
    this.numCanonicals = numCanonicals;
  }

  public int getNumNidx() {
    return numNidx;
  }

  public void setNumNidx(int numNidx) {
    this.numNidx = numNidx;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    MatchingStorageMetadata metadata = (MatchingStorageMetadata) o;
    return datasetKey == metadata.datasetKey && numUsages == metadata.numUsages && numCanonicals == metadata.numCanonicals && numNidx == metadata.numNidx && Objects.equals(alias, metadata.alias) && Objects.equals(title, metadata.title) && Objects.equals(version, metadata.version) && Objects.equals(created, metadata.created);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, alias, title, version, numUsages, numCanonicals, numNidx, created);
  }
}
