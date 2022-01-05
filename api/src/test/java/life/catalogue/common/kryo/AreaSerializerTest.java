package life.catalogue.common.kryo;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.vocab.*;

import org.junit.Test;

import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;

import static org.junit.Assert.assertEquals;

public class AreaSerializerTest {
  final AreaSerializer serializer = new AreaSerializer();
  final String text = "chitra: Thailand (NW as far as the Khwae Noi and Khwae Yai River basins of the Mae Klong River system; NE in the Mae Ping River basin of the Chao Phraya River system).";

  @Test
  public void parse() {
    var area = AreaSerializer.parse("Aha");
    assertEquals(new AreaImpl("Aha"), area);

    area = AreaSerializer.parse("iso:de");
    assertEquals(Country.GERMANY, area);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failBadIso() {
    AreaSerializer.parse("iso:ttt");
  }

  @Test(expected = IllegalArgumentException.class)
  public void failBadGazetteer() {
    AreaSerializer.parse("gaz:1234");
  }

  @Test
  public void roundtrip() {
    Area area;
    for (Gazetteer g : Gazetteer.values()) {
      switch (g) {
        case ISO:
          area = Country.ANGOLA;
          break;
        case TDWG:
          area = TdwgArea.AREAS.get(50);
          break;
        case LONGHURST:
          area = LonghurstArea.AREAS.get(2);
          break;
        case TEXT:
          area = new AreaImpl(text);
          break;
        default:
          area = new AreaImpl(g, RandomUtils.randomUri().toASCIIString(), null);
      }
      System.out.println(area);
      ByteBufferOutput out = new ByteBufferOutput(100, 100000);
      serializer.write(null, out, area);

      Input input = new Input(out.toBytes());
      Area area2 = serializer.read(null, input, null);
      assertEquals(area, area2);
    }
  }
}