package life.catalogue.common.id;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class IdConverterTest {
  
  @Test
  public void encode() {
    IdConverter dec = new IdConverter("0123456789");
    for (int x = 0; x<111; x++) {
      assertEquals(String.valueOf(x), dec.encode(x));
    }
    assertEquals("2", IdConverter.LATIN32.encode(0));
    assertEquals("32", IdConverter.LATIN32.encode(32));

    int[] ids = new int[]{0, 18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    List<IdConverter> converters = new ArrayList<>();
    converters.add(IdConverter.HEX);
    converters.add(IdConverter.LATIN32);
    converters.add(IdConverter.LATIN36);
    converters.add(IdConverter.BASE64);
  
    for (int id : ids) {
      System.out.println("\n" + id);
      for (IdConverter hid : converters) {
        System.out.println(hid.encode(id));
      }
      System.out.println(Proquint.encode(id));
    }
  }

  /**
   * We use the alphabetical sorting
   */
  @Test
  public void sorting() {
    System.out.println("a comparesTo b: " + "a".compareTo("b"));
    System.out.println("a comparesTo a: " + "a".compareTo("a"));
    System.out.println("a comparesTo 1: " + "a".compareTo("1"));
    System.out.println("a comparesTo 1a: " + "a".compareTo("1a"));
    System.out.println("a comparesTo a1: " + "a".compareTo("1"));

    List<String> xxx = new ArrayList<>(List.of("a", "b", "1", "a1", "aa", "a1a", "ab", "b1", "1a"));
    Collections.sort(xxx);
    for (String id : xxx) System.out.println(id);

    List<IdConverter> converters = List.of(
      IdConverter.LATIN32,
      IdConverter.LATIN36,
      IdConverter.HEX
    );

    for (IdConverter conv : converters) {
      List<String> ids = new ArrayList<>();
      for (char c : conv.getChars()) {
        ids.add(Character.toString(c));
      }
      System.out.println("--- CONVERTER " + ids.size());
      for (String id : ids) System.out.println(id);

      List<String> ids2 = new ArrayList<>(ids);
      Collections.sort(ids2);
      System.out.println("-- SORTED ---");
      for (String id : ids2) System.out.println(id);
      assertEquals(ids2, ids);
    }
  }

  @Test
  public void roundtrip() {
    IdConverter conv = new IdConverter("0123456789");
    System.out.println("Decimal");
    for (int x = 0; x<111; x++) {
      String id = conv.encode(x);
      System.out.println(x + " -> " + id + " -> " + conv.decode(id));
      assertEquals(x, conv.decode(id));
    }
    assertEquals(0, IdConverter.LATIN32.decode("2"));

    int[] vals = new int[]{0, 18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    List<IdConverter> converters = new ArrayList<>();
    converters.add(IdConverter.HEX);
    converters.add(IdConverter.LATIN32);
    converters.add(IdConverter.LATIN36);
    converters.add(IdConverter.BASE64);

    for (int val : vals) {
      System.out.println("\n" + val);
      for (IdConverter con : converters) {
        String id = con.encode(val);
        System.out.println(val + " -> " + id + " -> " + con.decode(id));
        assertEquals(val, con.decode(id));
      }
    }
  }
}