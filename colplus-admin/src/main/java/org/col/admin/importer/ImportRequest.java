package org.col.admin.importer;

import java.time.LocalDateTime;

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

}
