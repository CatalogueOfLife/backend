package org.col.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

import com.google.common.collect.EvictingQueue;
import org.col.api.model.SectorImport;

public class AssemblyState {
  public final SectorImport running;
  public final List<SectorImport> queued = new ArrayList<>();
  public final int failed;
  public final int completed;
  
  public AssemblyState(Collection<AssemblyCoordinator.SectorFuture> syncs, int syncsFailed, int syncsCompleted) {
    SectorImport run = null;
    for (AssemblyCoordinator.SectorFuture sync : syncs) {
      if (sync.state.getState() == SectorImport.State.WAITING) {
        queued.add(sync.state);
      } else if(sync.state.getState().isRunning()) {
        run = sync.state;
      } else {
        // should not be the case
        throw new IllegalStateException("Non running or waiting sync with state "+sync.state.getState()+" found in queue for sector " + sync.sectorKey);
      }
    }
    this.running = run;
    this.failed  = syncsFailed;
    this.completed= syncsCompleted;
  }
  
  public boolean isIdle() {
    return running == null && queued.isEmpty();
  }
}
