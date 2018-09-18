package org.col.admin.importer;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdGeneratorTest {

  @Test
  public void prefixed() {
    IdGenerator gen = IdGenerator.prefixed("ice");
    assertEquals("ice94QL", gen.next());
  }

  @Test
  public void excludeIds() {
    IdGenerator gen = IdGenerator.prefixed(Arrays.stream(new String[]{"ice", "12", "214", "-8", "a"}));
    assertEquals(".94QL", gen.next());

    gen = IdGenerator.prefixed(Arrays.stream(new String[]{".ice", "..12", "214", "-8", "a"}));
    assertEquals(".!94QL", gen.next());

  }
}