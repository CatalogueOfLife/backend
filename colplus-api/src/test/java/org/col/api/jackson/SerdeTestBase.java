package org.col.api.jackson;

import com.fasterxml.jackson.databind.JavaType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
abstract class SerdeTestBase<T> {

  Class<T> clazz;
  protected final JavaType type;

  public SerdeTestBase(Class<T> clazz) {
    this.clazz = clazz;
    type = ApiModule.MAPPER.getTypeFactory().constructParametricType(Wrapper.class, clazz);
  }

  public static class Wrapper<T> {
    public T value;

    public Wrapper(){}

    public Wrapper(T value){
      this.value = value;
    }
  }

  abstract T genTestValue() throws Exception;

  @Test
  public void testRoundtrip() throws Exception {
    testRoundtrip(genTestValue());
  }

  protected  void testRoundtrip(T value) throws Exception {
    Wrapper<T> wrapper = new Wrapper<T>(value);
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    System.out.println(json);
    Wrapper<T> wrapper2 = ApiModule.MAPPER.readValue(json, type);
    assertEquals(wrapper.value, wrapper2.value);
  }
}
