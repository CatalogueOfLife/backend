package life.catalogue.concurrent;

import java.util.UUID;

public class DatasetBlockedException extends RuntimeException {
  public final int datasetKey;
  public final UUID blockedBy;

  DatasetBlockedException(UUID blockedBy, int datasetKey) {
    this.blockedBy = blockedBy;
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
