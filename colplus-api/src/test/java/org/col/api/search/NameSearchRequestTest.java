package org.col.api.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameSearchRequestTest {
  
  @Test(expected = IllegalArgumentException.class)
  public void badInt() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "fgh");
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void badEnum() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.RANK, "spezi");
  }
  
  @Test
  public void addFilterGood() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "123 ");
    r.addFilter(NameSearchParameter.DATASET_KEY, 1234);
    assertEquals(ImmutableList.of("123", "1234"), r.get(NameSearchParameter.DATASET_KEY));
    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableList.of("123", "1234", "1234", "12", "13", "14"), r.get(NameSearchParameter.DATASET_KEY));
  
    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList("1", "2"));
    assertEquals(ImmutableList.of("123", "1234", "1234", "12", "13", "14", "1", "2"), r.get(NameSearchParameter.DATASET_KEY));
  }
  
  @Test
  public void allFilterParams() {
    NameSearchRequest r = new NameSearchRequest();
    
    for (NameSearchParameter p : NameSearchParameter.values()) {
      String val = testVal(p);
      r.addFilter(p, val);
      r.addFilter(p, Lists.newArrayList(val, val));
      assertEquals(ImmutableList.of(val, val, val), r.get(p));
    }
    assertEquals(NameSearchParameter.values().length, r.size());
  }
  
  private String testVal(NameSearchParameter p) {
    if (String.class.isAssignableFrom(p.type())) {
      return "E45 franz";
    } else if (Integer.class.isAssignableFrom(p.type())) {
      return "23456";
    } else if (p.type().isEnum()) {
      Enum[] values = ((Class<? extends Enum<?>>) p.type()).getEnumConstants();
      return values[0].name().toLowerCase();
    } else {
      throw new IllegalStateException(NameSearchParameter.class.getSimpleName() + " missing converter for data type " + p.type());
    }
  }
}