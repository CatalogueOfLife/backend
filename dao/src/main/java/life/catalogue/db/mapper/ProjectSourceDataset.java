package life.catalogue.db.mapper;

import life.catalogue.api.model.ArchivedDataset;

import java.util.Objects;

/**
 * Mapper internal class to bundle a dataset to a project key
 */
public class ProjectSourceDataset extends ArchivedDataset {
  private Integer datasetKey;

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ProjectSourceDataset that = (ProjectSourceDataset) o;
    return Objects.equals(datasetKey, that.datasetKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetKey);
  }
}
