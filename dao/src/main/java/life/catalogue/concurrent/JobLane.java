package life.catalogue.concurrent;

/**
 * The named queues of the JobExecutor.
 * Each lane has its own worker pool and priority queue, configured in JobConfig,
 * so long running imports or syncs cannot starve regular background jobs.
 */
public enum JobLane {

  /**
   * Regular background jobs: exports, releases, matching, admin jobs.
   */
  DEFAULT,

  /**
   * Dataset imports.
   */
  IMPORT,

  /**
   * Sector syncs and deletions.
   * Jobs in this lane are serialized per project via BackgroundJob.getSerialBy(),
   * only ever running a single job for the same project at the same time.
   */
  SYNC;
}
