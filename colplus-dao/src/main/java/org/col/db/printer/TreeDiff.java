package org.col.db.printer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.difflib.text.DiffRow;

public class TreeDiff {
  private final int sectorKey;
  private final int attempt1;
  private final int attempt2;
  private final Map<DiffRow.Tag, AtomicInteger> summary = new HashMap<>();
  private final List<Row> rows = new ArrayList<>();
  
  public TreeDiff(int sectorKey, int attempt1, int attempt2) {
    this.sectorKey = sectorKey;
    this.attempt1 = attempt1;
    this.attempt2 = attempt2;
  }
  
  public static class Row {
    static final char EQUAL  = '=';
    static final char DELETE = '-';
    static final char INSERT = '+';
    static final char CHANGE = '~';
  
    public final char op;
    public final String old;
    @JsonProperty("new")
    public final String _new;
  
    public Row(char op, String old, String _new) {
      this.op = op;
      this.old = old;
      this._new = _new;
    }
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
  
  public List<Row> getRows() {
    return rows;
  }
  
  public Map<DiffRow.Tag, AtomicInteger> getSummary() {
    return summary;
  }
  
  private void incSummary(DiffRow.Tag op) {
    if (!summary.containsKey(op)) {
      summary.put(op, new AtomicInteger(1));
    } else {
      summary.get(op).incrementAndGet();
    }
  }
  
  public void add(DiffRow row) {
    incSummary(row.getTag());
    if (row.getTag() == DiffRow.Tag.EQUAL) {
      rows.add(new Row(Row.EQUAL, row.getOldLine(), null));
    } else {
      char op;
      switch (row.getTag()) {
        case DELETE:
          op=Row.DELETE;
          break;
        case INSERT:
          op=Row.INSERT;
          break;
        default:
          op=Row.CHANGE;
      }
      rows.add(new Row(op, row.getOldLine(), row.getNewLine()));
    }
  }
  
}
