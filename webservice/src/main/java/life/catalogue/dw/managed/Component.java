package life.catalogue.dw.managed;

/**
 * Start/stoppable components in the order they should be started.
 */
public enum Component {
  NamesIndex,
  UsageCache,
  LegacyIdMap,
  JobExecutor,
  CronExecutor,
  DatasetImporter,
  SectorSynchronizer,
  ImportScheduler,
  GBIFRegistrySync
}
