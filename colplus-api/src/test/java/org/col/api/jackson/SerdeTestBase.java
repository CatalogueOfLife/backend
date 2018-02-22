package org.col.api.jackson;

import com.fasterxml.jackson.databind.JavaType;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SerdeTestBase<T extends Enum> {

  private Class<T> enumClass;
  private final JavaType type;

  public SerdeTestBase(Class<T> enumClass) {
    this.enumClass = enumClass;
    type = ApiModule.MAPPER.getTypeFactory().constructParametricType(Wrapper.class, enumClass);
  }

  public static class Wrapper<T> {
    public T value;

    public Wrapper(){}

    public Wrapper(T value){
      this.value = value;
    }
  }

  @Test
  public void testRoundtrip() throws IOException {
    for (T e : enumClass.getEnumConstants()) {
      Wrapper<T> wrapper = new Wrapper<T>(e);
      String json = ApiModule.MAPPER.writeValueAsString(wrapper);
      System.out.println(json);
      Wrapper<T> wrapper2 = ApiModule.MAPPER.readValue(json, type);
      assertEquals(wrapper.value, wrapper2.value);
    }
  }

}
