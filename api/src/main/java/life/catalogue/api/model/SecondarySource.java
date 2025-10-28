package life.catalogue.api.model;

import life.catalogue.api.vocab.InfoGroup;

import java.util.Objects;

public class SecondarySource implements DSID<String> {
  private String id; // the secondary source's name usage identifier
  private Integer datasetKey; // the secondary source's dataset key
  private InfoGroup type;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }

  @Override
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public InfoGroup getType() {
    return type;
  }

  public void setType(InfoGroup type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SecondarySource that = (SecondarySource) o;
    return Objects.equals(id, that.id) && Objects.equals(datasetKey, that.datasetKey) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, type);
  }
}
