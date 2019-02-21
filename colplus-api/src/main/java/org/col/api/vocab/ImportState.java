package org.col.api.vocab;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;

/**
 * Warning! If ordinals are changed please change also DatasetImportMapper.xml
 * which has a hardcoded number!
 */
public enum ImportState {
  
  /**
   * Downloading the latest source data, the first step of a running import.
   */
  DOWNLOADING(true),
  
  /**
   * Normalization of the dataset without touching the previous data in Postgres.
   */
  PROCESSING(true),
  
  /**
   * Inserting data into Postgres, starts by wiping any previous edition.
   */
  INSERTING(true),
  
  /**
   * Indexing data into the Elastic Search index.
   */
  INDEXING(true),
  
  /**
   * Indexing data into the Elastic Search index.
   */
  BUILDING_METRICS(true),

  /**
   * Sources have not been changed since last import. Imported stopped.
   */
  UNCHANGED,
  
  /**
   * Successfully completed the import.
   */
  FINISHED,
  
  /**
   * Manually aborted import, e.g. system was shut down.
   */
  CANCELED,
  
  /**
   * Import failed due to errors.
   */
  FAILED;
  
  ImportState() {
    this.running = false;
  }

  ImportState(boolean running) {
    this.running = running;
  }
  
  private final boolean running;
  
  public boolean isRunning() {
    return running;
  }
  
  public static List<ImportState> runningStates() {
    return Arrays.stream(values())
        .filter(ImportState::isRunning)
        .collect(Collectors.toList());
  }
  
  public static List<ImportState> finishedStates() {
    return Arrays.stream(values())
        .filter(Predicates.not(ImportState::isRunning))
        .collect(Collectors.toList());
  }
}
