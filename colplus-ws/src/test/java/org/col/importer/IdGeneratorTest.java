package org.col.importer;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdGeneratorTest {
  
  @Test
  public void prefixed() {
    IdGenerator gen = new IdGenerator("ice");
    assertEquals("ice6aOv", gen.next());
  }
  
  @Test
  public void excludeIds() {
    IdGenerator gen = new IdGenerator().setPrefix(Arrays.stream(new String[]{"ice", "12", "214", "-8", "a"}));
    assertEquals("x6aOv", gen.next());
    
    gen = new IdGenerator().setPrefix(Arrays.stream(new String[]{"xice", "xx12", "214", "-8", "a", "0321"}));
    assertEquals("x-6aOv", gen.next());
    
  }
}