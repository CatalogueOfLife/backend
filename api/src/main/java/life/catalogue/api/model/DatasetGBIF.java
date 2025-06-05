package life.catalogue.api.model;

import java.util.Objects;
import java.util.UUID;

public class DatasetGBIF extends DatasetSimple {
  private UUID gbifKey;

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetGBIF)) return false;
    if (!super.equals(o)) return false;

    DatasetGBIF that = (DatasetGBIF) o;
    return Objects.equals(gbifKey, that.gbifKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), gbifKey);
  }
}
