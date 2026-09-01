package life.catalogue.dw.managed;

/**
 * Start/stoppable components in the order they should be started.
 */
public enum Component {
  NamesIndex,
  JobExecutor,
  UsageMatcher,
  CronExecutor,
  DoiUpdater,
  DatasetImporter,
  SectorSynchronizer,
  ImportScheduler,
  SyncScheduler,
  GBIFRegistrySync,
  Feedback
}
