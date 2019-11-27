package life.catalogue.common.id;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdConverterTest {
  
  @Test
  public void encode() {
    IdConverter dec = new IdConverter("0123456789");
    for (int x = 0; x<111; x++) {
      assertEquals(String.valueOf(x), dec.encode(x));
    }
    assertEquals("2", IdConverter.LATIN32.encode(0));

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
}