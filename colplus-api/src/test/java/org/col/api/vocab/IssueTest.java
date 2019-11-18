package org.col.api.vocab;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Stopwatch;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class IssueTest {
  
  @Test
  @Ignore
  public void showOrdinals() {
    for (Issue i : Issue.values()) {
      System.out.println(i.ordinal() + " -> " + i.key + " = " + i.name());
    }
  }
  
  @Test
  public void uniqueKeys() {
    Set<Integer> keys = new HashSet<>();
    for (Issue i : Issue.values()) {
      if (keys.contains(i.key)) {
        fail("Key "+i.key + " is not unique");
      }
      keys.add(i.key);
    }
  }
  
  @Test
  public void fromKey() {
    assertEquals(Issue.DUPLICATE_NAME, Issue.fromKey(Issue.DUPLICATE_NAME.key));
  
    Stopwatch watch = Stopwatch.createUnstarted();
    for (int repeat=0; repeat<3; repeat++){
      watch.reset();
      watch.start();
      for (int x = 0; x<1000000; x++) {
        Issue iss = Issue.fromKey(4);
      }
      watch.stop();
      System.out.println("run " + repeat + ": " + watch);
    }
  }
}