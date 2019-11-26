package org.col.api.vocab;

import org.junit.Test;


public class IssueTest {
  
  @Test
  public void showOrdinals() {
    for (Issue i : Issue.values()) {
      System.out.println(i.ordinal() + " -> " + i.name());
    }
  }
  
}