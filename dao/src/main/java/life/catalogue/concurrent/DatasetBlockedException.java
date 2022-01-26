package life.catalogue.concurrent;

import java.util.UUID;

public class DatasetBlockedException extends BlockedException {
  public final int datasetKey;

  DatasetBlockedException(UUID blockedBy, int datasetKey) {
    super(blockedBy);
    this.datasetKey = datasetKey;
  }

  @Override
  public String toString() {
    return "DatasetBlockedException{" +
      "datasetKey=" + datasetKey +
      ", blockedBy=" + blockedBy +
      '}';
  }
}
