package org.col.admin.importer;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdGeneratorTest {
  
  @Test
  public void prefixed() {
    IdGenerator gen = new IdGenerator("ice");
    assertEquals("ice94QL", gen.next());
  }
  
  @Test
  public void excludeIds() {
    IdGenerator gen = new IdGenerator().setPrefix(Arrays.stream(new String[]{"ice", "12", "214", "-8", "a"}));
    assertEquals(".94QL", gen.next());
    
    gen = new IdGenerator().setPrefix(Arrays.stream(new String[]{".ice", "..12", "214", "-8", "a"}));
    assertEquals(".!94QL", gen.next());
    
  }
}