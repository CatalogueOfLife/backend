package org.col.admin.importer;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 *
 */
public class ImportRequest {
  public final int datasetKey;
  public final boolean force;
  public final LocalDateTime created = LocalDateTime.now();
  public LocalDateTime started;

  public ImportRequest(int datasetKey, boolean force) {
    this.datasetKey = datasetKey;
    this.force = force;
  }

  public void start() {
    started = LocalDateTime.now();
  }

  /**
   * Naturally equal if the datasetKey matches
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImportRequest that = (ImportRequest) o;
    return datasetKey == that.datasetKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey);
  }

  @Override
  public String toString() {
    return "ImportRequest{" +
        "datasetKey=" + datasetKey +
        ", force=" + force +
        '}';
  }
}
