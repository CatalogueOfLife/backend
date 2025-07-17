package life.catalogue.api.jackson;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Reference;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.JavaType;

import static org.junit.Assert.*;

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
    System.out.println(json);
    assertSerialisation(json);
  }

  protected void assertSerialisation(String json) {
    // nothing by default, override to test sth specific
  }

  protected String serialize() throws Exception {
    return ApiModule.MAPPER.writeValueAsString(new Wrapper<T>(genTestValue()));
  }

  protected String testRoundtrip(T value) throws Exception {
    Wrapper<T> wrapper = new Wrapper<T>(value);
    String json = ApiModule.MAPPER.writeValueAsString(wrapper);
    System.out.println(json);
    Wrapper<T> wrapper2 = ApiModule.MAPPER.readValue(json, type);
    assertEquals(wrapper.value, wrapper2.value);
    return json;
  }

  @Test
  public void testMinimumEquals() throws Exception {
    T obj1 = clazz.getConstructor().newInstance();
    T obj2 = clazz.getConstructor().newInstance();
    // we don't care if it is actually equal - we likely generate different objects
    // we only want to test
    assertEquals(obj1, obj2);

    // we don't care if it is actually equal - we likely generate different objects
    // we only want to test equals not to throw
    obj1 = genTestValue();
    obj2 = genTestValue();
    obj1.equals(obj2);
  }
}
