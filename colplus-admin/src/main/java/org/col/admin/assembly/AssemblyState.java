package org.col.admin.assembly;

import java.util.List;

public class AssemblyState {
  public final List<SectorSyncState> syncsRunning;
  public final int syncsFailed;
  public final int syncsCompleted;
  public final String note;
  
  public AssemblyState(List<SectorSyncState> syncsRunning, int syncsFailed, int syncsCompleted, String note) {
    this.syncsRunning = syncsRunning;
    this.syncsFailed = syncsFailed;
    this.syncsCompleted = syncsCompleted;
    this.note = note;
  }
  
  public boolean isIdle() {
    return syncsRunning.isEmpty();
  }
}
