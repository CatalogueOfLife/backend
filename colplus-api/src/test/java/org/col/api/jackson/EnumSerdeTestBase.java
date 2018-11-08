package org.col.api.jackson;

import org.junit.Test;

/**
 *
 */
public class EnumSerdeTestBase<T extends Enum> extends SerdeTestBase<T> {
  
  public EnumSerdeTestBase(Class<T> enumClass) {
    super(enumClass);
  }
  
  @Override
  public T genTestValue() throws Exception {
    return clazz.getEnumConstants()[0];
  }
  
  @Test
  public void testAllEnumValues() throws Exception {
    for (T e : clazz.getEnumConstants()) {
      testRoundtrip(e);
    }
  }
  
}
