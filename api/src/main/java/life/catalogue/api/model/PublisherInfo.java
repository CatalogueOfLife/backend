package life.catalogue.api.model;

import java.util.Objects;

/**
 * A GBIF publisher with the number of ChecklistBank datasets it publishes, as listed by the publisher search.
 * Deleted and temporary datasets are not counted, nor private ones the user cannot read.
 */
public class PublisherInfo extends Publisher {

  public enum SortBy {
    /**
     * Best matches of the query first, falling back to DATASETS without a query.
     */
    RELEVANCE,
    TITLE,
    /**
     * Most datasets first.
     */
    DATASETS
  }

  private int datasets;

  public int getDatasets() {
    return datasets;
  }

  public void setDatasets(int datasets) {
    this.datasets = datasets;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PublisherInfo)) return false;
    if (!super.equals(o)) return false;
    PublisherInfo that = (PublisherInfo) o;
    return datasets == that.datasets;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasets);
  }
}
