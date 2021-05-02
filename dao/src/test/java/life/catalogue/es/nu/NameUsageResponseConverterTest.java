package life.catalogue.es.nu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

public class NameUsageResponseConverterTest {

  @Test
  // Mainly just tests how number strings get deserialized using Jackson/EsModule. Integer, Long ...
  public void testIntString() throws IOException {
    ObjectMapper om = new ObjectMapper();
    String s = "42";
    Object obj = om.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: Integer
    assertEquals(Integer.class, obj.getClass());

    s = "429898989898";
    obj = om.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: Long
    assertEquals(Long.class, obj.getClass());

    s = "4298989898986546354343543543";
    obj = om.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: BigInteger
    assertEquals(BigInteger.class, obj.getClass());

    /*
     * In short (...) if we want to be thorough and avoid even the slightest chance of class cast exceptions we should be
     * prepared for anything.
     */
  }

}
