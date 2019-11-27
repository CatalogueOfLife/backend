package life.catalogue.api.jackson;

import com.fasterxml.jackson.databind.JavaType;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 *
 */
public abstract class SerdeTestBase<T> {
  
  protected final Class<T> clazz;
  protected final JavaType type;
  
  public SerdeTestBase(Class<T> clazz) {
    this.clazz = clazz;
    type = ApiModule.MAPPER.getTypeFactory().constructParametricType(Wrapper.class, clazz);
  }
  
  public static class Wrapper<T> {
    public T value;
    
    public Wrapper() {
    }
    
    public Wrapper(T value) {
      this.value = value;
    }
  }
  
  public abstract T genTestValue() throws Exception;
  
  @Test
  public void testRoundtrip() throws Exception {
    testRoundtrip(genTestValue());
  }
  
  @Test
  public void testSerialisation() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(new Wrapper<T>(genTestValue()));
    assertFalse(StringUtils.isBlank(json));
  }
  
  protected String serialize() throws Exception {
    return ApiModule.MAPPER.writeValueAsString(new Wrapper<T>(genTestValue()));
  }

  protected void testRoundtrip(T value) throws Exception {
    Wrapper<T> wrapper = new Wrapper<T>(value);
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    Wrapper<T> wrapper2 = ApiModule.MAPPER.readValue(json, type);
    debug(json, wrapper, wrapper2);
    assertEquals(wrapper.value, wrapper2.value);
  }
  
  protected void debug(String json, Wrapper<T> wrapper, Wrapper<T> wrapper2) {
    System.out.println(json);
  }
}
