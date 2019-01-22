package org.col.admin.assembly;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class SectorSyncState {
  public enum Status {
    WAITING, PREPARING, COPYING, DELETING, INDEXING, FINISHED
  }
  
  public Status status = Status.WAITING;
  public LocalDateTime started;
  public final AtomicInteger created = new AtomicInteger(0);
  public final AtomicInteger updated = new AtomicInteger(0);
  public final AtomicInteger deleted = new AtomicInteger(0);
}
