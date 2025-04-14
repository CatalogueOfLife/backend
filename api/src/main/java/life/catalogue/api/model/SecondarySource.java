package life.catalogue.api.model;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.InfoGroup;

import java.util.Objects;

import static life.catalogue.api.vocab.EntityType.NAME_USAGE;

public class SecondarySource implements DSID<String> {
  private String id;
  private Integer datasetKey;
  private EntityType entity = NAME_USAGE; // currently only NameUsage, Name or Reference is supported!
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

  public EntityType getEntity() {
    return entity;
  }

  public void setEntity(EntityType entity) {
    this.entity = entity;
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
    return Objects.equals(id, that.id) && Objects.equals(datasetKey, that.datasetKey) && entity == that.entity && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, datasetKey, entity, type);
  }
}
