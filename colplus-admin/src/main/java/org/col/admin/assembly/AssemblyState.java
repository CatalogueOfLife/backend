package org.col.admin.assembly;

import java.util.ArrayList;
import java.util.List;

import org.col.api.model.SectorImport;

public class AssemblyState {
  public final SectorImport running;
  public final List<SectorImport> queued = new ArrayList<>();
  public final int failed;
  public final int completed;
  
  public AssemblyState(List<SectorImport> syncs, int syncsFailed, int syncsCompleted) {
    SectorImport run = null;
    for (SectorImport sync : syncs) {
      if (sync.getState() == SectorImport.State.WAITING) {
        queued.add(sync);
      } else if(sync.getState().isRunning()) {
        run = sync;
      } else {
        // he?
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
