package org.col.api.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameSearchRequestTest {
  
  @Test(expected = IllegalArgumentException.class)
  public void addFilterBad() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "fgh");
  }
  
  @Test
  public void addFilterGood() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchParameter.DATASET_KEY, "123");
    r.addFilter(NameSearchParameter.DATASET_KEY, 1234);
    assertEquals(ImmutableList.of("123", "1234"), r.get(NameSearchParameter.DATASET_KEY));
    r.addFilter(NameSearchParameter.DATASET_KEY, Lists.newArrayList(1234, 12, 13, 14));
    assertEquals(ImmutableList.of("123", "1234", "1234", "12", "13", "14"), r.get(NameSearchParameter.DATASET_KEY));
  }
}