package org.col.admin.assembly;

public class AssemblyState {
  public final int syncsRunning;
  public final int syncsFailed;
  public final int syncsCompleted;
  public final String note;
  
  public AssemblyState(int syncsRunning, int syncsFailed, int syncsCompleted) {
    this(syncsRunning, syncsFailed, syncsCompleted, null);
  }
  
  public AssemblyState(int syncsRunning, int syncsFailed, int syncsCompleted, String note) {
    this.syncsRunning = syncsRunning;
    this.syncsFailed = syncsFailed;
    this.syncsCompleted = syncsCompleted;
    this.note = note;
  }
  
  public boolean isIdle() {
    return syncsRunning==0;
  }
}
