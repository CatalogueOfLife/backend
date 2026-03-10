package life.catalogue.api.model;

import java.net.URI;
import java.util.Objects;

public class SimpleNameInDataset extends SimpleName {
  private int datasetKey;
  private URI link;

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public URI getLink() {
    return link;
  }

  public void setLink(URI link) {
    this.link = link;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleNameInDataset that)) return false;
    if (!super.equals(o)) return false;

    return datasetKey == that.datasetKey &&
      Objects.equals(link, that.link);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetKey, link);
  }
}
