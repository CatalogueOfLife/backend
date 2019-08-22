package org.col.es.name;

import java.io.IOException;
import java.math.BigInteger;

import org.col.es.EsModule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameSearchResponseTransferTest {

  @Test
  // Eh ... how do number strings get deserialized using EsModule? Integer or Long?
  public void testIntString() throws IOException {
    String s = "42";
    Object obj = EsModule.MAPPER.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: Integer
    assertEquals(Integer.class, obj.getClass());

    s = "429898989898";
    obj = EsModule.MAPPER.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: Long
    assertEquals(Long.class, obj.getClass());

    s = "4298989898986546354343543543";
    obj = EsModule.MAPPER.readValue(s, Object.class);
    System.out.println(obj.getClass());
    // Answer: BigInteger
    assertEquals(BigInteger.class, obj.getClass());

    /*
     * In short (...) if we want to be thorough and avoid even the slightest chance of class cast exceptions we should be prepared for
     * anything.
     */
  }

}
