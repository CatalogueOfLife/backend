package org.col.api.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.google.common.collect.Maps;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SerdeMapKeyTestBase<T extends Enum> extends SerdeTestBase<T> {

  public SerdeMapKeyTestBase(Class<T> enumClass) {
    super(enumClass);
  }

  public static class MapWrapper<T> {
    public Map<T, T> map = Maps.newHashMap();
  }

  @Test
  public void testMapKey() throws IOException {
    JavaType type = ApiModule.MAPPER.getTypeFactory().constructParametricType(MapWrapper.class, enumClass);
    MapWrapper<T> wrapper = new MapWrapper<T>();

    // check empty first
    testRoundtrip(wrapper, type);

    // check with one entry first
    T val = enumClass.getEnumConstants()[0];
    wrapper.map.put(val, val);
    testRoundtrip(wrapper, type);

    // now add all values as keys
    for (T e : enumClass.getEnumConstants()) {
      wrapper.map.put(e, e);
    }
    testRoundtrip(wrapper, type);
  }

  private static <T> void testRoundtrip(MapWrapper<T> wrapper, JavaType type) throws IOException {
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    System.out.println(json);
    MapWrapper<T> wrapper2 = ApiModule.MAPPER.readValue(json, type);

    //Javers javers = JaversBuilder.javers().build();
    //Diff diff = javers.compare(wrapper.map, wrapper2.map);
    //System.out.println(diff);

    assertEquals(wrapper.map, wrapper2.map);
  }
}
