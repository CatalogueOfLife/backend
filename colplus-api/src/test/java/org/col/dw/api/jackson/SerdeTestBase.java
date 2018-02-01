package org.col.dw.api.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class SerdeTestBase<T extends Enum> {

  static final ObjectMapper MAPPER = new ObjectMapper();

  private Class<T> enumClass;
  private final JavaType type;

  public SerdeTestBase(Class<T> enumClass, SimpleModule serdeModule) {
    this.enumClass = enumClass;
    MAPPER.registerModule(serdeModule);
    type = MAPPER.getTypeFactory().constructParametricType(Wrapper.class, enumClass);
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
      String json = MAPPER.writeValueAsString(wrapper);
      Wrapper<T> wrapper2 = MAPPER.readValue(json, type);
      assertEquals(wrapper.value, wrapper2.value);
    }
  }

}
