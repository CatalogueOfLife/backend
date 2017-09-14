package org.col.commands.importer;

import org.col.db.mapper.DatasourceMetricsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Analyses an entire dataset and persists a newly generated DatasetMetrics.
 */
public class Analyser implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(Analyser.class);

  private final int key;
  private final DatasourceMetricsMapper mapper;

  private Analyser(int key, DatasourceMetricsMapper mapper) {
    this.key = key;
    this.mapper = mapper;
  }

  /**
   * Creates a dataset specific analyser
   */
  public static Analyser create(int key, DatasourceMetricsMapper mapper) {
    return new Analyser(key, mapper);
  }

  @Override
  public void run() {
    LOG.info("Create new metrics for dataset {}", key);
    mapper.insert(key, new Date());
  }
}
