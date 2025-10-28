package life.catalogue.dw.managed;

/**
 * Start/stoppable components in the order they should be started.
 */
public enum Component {
  NamesIndex,
  UsageCache,
  JobExecutor,
  CronExecutor,
  DatasetImporter,
  SectorSynchronizer,
  ImportScheduler,
  GBIFRegistrySync,
  Feedback
}
