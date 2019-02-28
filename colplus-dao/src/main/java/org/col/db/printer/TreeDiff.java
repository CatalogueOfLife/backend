package org.col.db.printer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.difflib.text.DiffRow;

public class TreeDiff {
  private final int sectorKey;
  private final int attempt1;
  private final int attempt2;
  private final Map<DiffRow.Tag, AtomicInteger> summary = new HashMap<>();
  private final List<DiffRow> rows = new ArrayList<>();
  
  public TreeDiff(int sectorKey, int attempt1, int attempt2) {
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
  
  public List<DiffRow> getRows() {
    return rows;
  }
  
  public Map<DiffRow.Tag, AtomicInteger> getSummary() {
    return summary;
  }
  
  public void incSummary(DiffRow.Tag tag) {
    if (!summary.containsKey(tag)) {
      summary.put(tag, new AtomicInteger(1));
    } else {
      summary.get(tag).incrementAndGet();
    }
  }
  
  public void add(DiffRow row) {
    rows.add(row);
  }
}
