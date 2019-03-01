package org.col.db.tree;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.difflib.patch.DeltaType;

public abstract class DiffReport {
  private final int sectorKey;
  private final int attempt1;
  private final int attempt2;
  private final Map<DeltaType, AtomicInteger> summary = new HashMap<>();
  
  private DiffReport(int sectorKey, int attempt1, int attempt2) {
    this.sectorKey = sectorKey;
    this.attempt1 = attempt1;
    this.attempt2 = attempt2;
  }
  
  public int getSectorKey() {
    return sectorKey;
  }
  
  public int getAttempt1() {
    return attempt1;
  }
  
  public int getAttempt2() {
    return attempt2;
  }
  
  public Map<DeltaType, AtomicInteger> getSummary() {
    return summary;
  }
  
  public void setSummary(DeltaType op, int count) {
    summary.put(op, new AtomicInteger(count));
  }
  
  void incSummary(DeltaType op) {
    if (!summary.containsKey(op)) {
      summary.put(op, new AtomicInteger(1));
    } else {
      summary.get(op).incrementAndGet();
    }
  }
  
  @Override
  public String toString() {
    return "{" +
        "sectorKey=" + sectorKey +
        ", attempt1=" + attempt1 +
        ", attempt2=" + attempt2 +
        ", summary=" + summary;
  }
  
  public static class TreeDiff extends DiffReport {
    private String diff;
    
    public TreeDiff(int sectorKey, int attempt1, int attempt2) {
      super(sectorKey, attempt1, attempt2);
    }
  
    public String getDiff() {
      return diff;
    }
  
    public void setDiff(String diff) {
      this.diff = diff;
    }
  
    @Override
    public String toString() {
      return "TreeDiff" +
          super.toString() +
          ", diff=\n" + diff;
    }
  }
  
  public static class NamesDiff extends DiffReport {
    private Set<String> deleted;
    private Set<String> inserted;
    
    public NamesDiff(int sectorKey, int attempt1, int attempt2) {
      super(sectorKey, attempt1, attempt2);
    }
    
    public Set<String> getDeleted() {
      return deleted;
    }
  
    public void setDeleted(Set<String> deleted) {
      this.deleted = deleted == null ? Collections.EMPTY_SET : deleted;
      setSummary(DeltaType.DELETE, this.deleted.size());
    }
  
    public void setInserted(Set<String> inserted) {
      this.inserted = inserted == null ? Collections.EMPTY_SET : inserted;
      setSummary(DeltaType.INSERT, this.inserted.size());
    }
  
    public Set<String> getInserted() {
      return inserted;
    }

    @Override
    public String toString() {
      return "NamesDiff" +
          super.toString() +
          ", deleted=" + deleted +
          ", inserted=" + inserted;
    }
  }
  
}
