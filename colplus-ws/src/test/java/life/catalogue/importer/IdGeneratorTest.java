package life.catalogue.importer;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class IdGeneratorTest {
  
  @Test
  public void prefixed() {
    IdGenerator gen = new IdGenerator("NI");
    assertEquals("NI3", gen.next());
  }
  
  @Test
  public void autoPrefix() {
    IdGenerator gen = new IdGenerator().setPrefix("x", Arrays.stream(new String[]{"ice", "12", "214", "-8", "a"}));
    assertEquals("x3", gen.next());
    
    gen = new IdGenerator(Arrays.stream(new String[]{"xice", "xx12", "214", "-8", "a", "0321"}));
    assertEquals("x0", gen.getPrefix());

    gen = new IdGenerator("-", Arrays.stream(new String[]{"xice", "xx12", "214", "-8", "a", "0321"}), 0);
    assertEquals("-x", gen.getPrefix());

    gen = new IdGenerator("tm", Arrays.stream(new String[]{"xice", "xx12", "214", "-8", "a", "0321"}), 0);
    assertEquals("tm", gen.getPrefix());
  }
  
}