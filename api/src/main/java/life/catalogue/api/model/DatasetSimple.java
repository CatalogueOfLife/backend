package life.catalogue.api.model;

import java.util.Objects;

public class DatasetSimple {
  private Integer key;
  private String alias;
  private String title;
  private String version;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DatasetSimple)) return false;
    DatasetSimple that = (DatasetSimple) o;
    return Objects.equals(key, that.key) && Objects.equals(alias, that.alias) && Objects.equals(title, that.title) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, alias, title, version);
  }
}
