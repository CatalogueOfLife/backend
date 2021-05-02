package life.catalogue.es.nu;

import org.junit.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

// Make sure MultiValuedMap works as expected (set produced MultiValuedMap.keySet() is backed by the map & vice versa).
public class MultiValuedMapTest {

  @Test
  public void removeAll() {
    MultivaluedMap<String, String> m = new MultivaluedHashMap<>();
    m.put("one", Arrays.asList("a"));
    m.put("two", Arrays.asList("b"));
    m.put("three", Arrays.asList("c"));
    m.put("four", Arrays.asList("d"));
    m.put("five", Arrays.asList("e"));
    m.put("six", Arrays.asList("f"));
    m.keySet().removeAll(Arrays.asList("two", "three", "seven"));
    assertEquals(4, m.size());
  }

  @Test
  public void retainAll() {
    MultivaluedMap<String, String> m = new MultivaluedHashMap<>();
    m.put("one", Arrays.asList("a"));
    m.put("two", Arrays.asList("b"));
    m.put("three", Arrays.asList("c"));
    m.put("four", Arrays.asList("d"));
    m.put("five", Arrays.asList("e"));
    m.put("six", Arrays.asList("f"));
    m.keySet().retainAll(Arrays.asList("two", "three", "seven"));
    assertEquals(2, m.size());
  }

}
