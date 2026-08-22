package life.catalogue.assembly;

import life.catalogue.api.model.SectorImport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * State of the sector syncs of one or all projects:
 * the currently running and queued syncs plus counts of completed and failed syncs since the server started.
 * With the sync lane of the job executor potentially running several syncs of different projects in parallel,
 * there can be more than one running sync.
 */
public class SyncState {

  public final List<SectorImport> running = new ArrayList<>();
  public final List<SectorImport> queued = new ArrayList<>();
  public final int failed;
  public final int completed;

  SyncState(Collection<SectorRunnable> jobs, int syncsFailed, int syncsCompleted) {
    for (SectorRunnable job : jobs) {
      if (job.isRunning()) {
        running.add(job.getState());
      } else if (job.isQueued()) {
        queued.add(job.getState());
      }
      // jobs that just ended can linger in queue snapshots - ignore them
    }
    this.failed  = syncsFailed;
    this.completed = syncsCompleted;
  }

  public boolean isIdle() {
    return running.isEmpty() && queued.isEmpty();
  }
}
