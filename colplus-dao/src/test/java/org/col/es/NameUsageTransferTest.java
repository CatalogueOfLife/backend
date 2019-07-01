package org.col.es;

import org.junit.Test;
import static org.junit.Assert.*;

public class NameUsageTransferTest {

  @Test
  public void testNormalizeWeakly1() {
    String s = NameUsageTransfer.normalizeWeakly("Larus");
    assertEquals("larus", s);
  }

  @Test
  public void testNormalizeWeakly2() {
    String s = NameUsageTransfer.normalizeWeakly("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalizeWeakly3() {
    String s = NameUsageTransfer.normalizeWeakly("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalizeStrongly1a() {
    String s = NameUsageTransfer.normalizeStrongly("Larus");
    System.out.println(s);
    assertEquals("lara", s);
  }

  @Test
  public void testNormalizeStrongly1b() {
    String s = NameUsageTransfer.normalizeStrongly("Larus fuscus");
    assertEquals("larus fusca", s);
  }

  @Test
  public void testNormalizeStrongly1c() {
    String s = NameUsageTransfer.normalizeStrongly("Larus fuscus fuscus");
    System.out.println(s);
    assertEquals("larus fuscus fusca", s);
  }

  @Test
  public void testNormalizeStrongly2() {
    String s = NameUsageTransfer.normalizeStrongly("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalizeStrongly3() {
    String s = NameUsageTransfer.normalizeStrongly("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalizeStrongly4() {
    String s = NameUsageTransfer.normalizeStrongly("sylvestris");
    System.out.println(s);
    assertEquals("silvestris", s);
  }

}
