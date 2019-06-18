package org.col.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.col.api.model.SectorImport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssemblyState {
  private static final Logger LOG = LoggerFactory.getLogger(AssemblyState.class);
  
  public final SectorImport running;
  public final List<SectorImport> queued = new ArrayList<>();
  public final int failed;
  public final int completed;
  
  AssemblyState(Collection<AssemblyCoordinator.SectorFuture> syncs, int syncsFailed, int syncsCompleted) {
    SectorImport run = null;
    for (AssemblyCoordinator.SectorFuture sync : syncs) {
      if (sync.state.getState() == SectorImport.State.WAITING) {
        queued.add(sync.state);
      } else if(sync.state.getState().isRunning()) {
        if (run != null) {
          LOG.error("Multiple running sector tasks. Ignore {} over {}", run, sync.state);
        }
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
