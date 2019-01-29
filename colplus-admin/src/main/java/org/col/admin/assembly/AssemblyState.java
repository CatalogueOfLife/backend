package org.col.admin.assembly;

import java.util.List;

import org.col.api.model.SectorImport;

public class AssemblyState {
  public final List<SectorImport> syncsRunning;
  public final int syncsFailed;
  public final int syncsCompleted;
  public final String note;
  
  public AssemblyState(List<SectorImport> syncsRunning, int syncsFailed, int syncsCompleted, String note) {
    this.syncsRunning = syncsRunning;
    this.syncsFailed = syncsFailed;
    this.syncsCompleted = syncsCompleted;
    this.note = note;
  }
  
  public boolean isIdle() {
    return syncsRunning.isEmpty();
  }
}
