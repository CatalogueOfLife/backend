package org.col.api.search;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameSearchRequestTest {
  
  @Test(expected = IllegalArgumentException.class)
  public void addFilterBad() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchPararmeter.DATASET_KEY, "fgh");
  }
  
  @Test
  public void addFilterGood() {
    NameSearchRequest r = new NameSearchRequest();
    r.addFilter(NameSearchPararmeter.DATASET_KEY, "123");
    r.addFilter(NameSearchPararmeter.DATASET_KEY, 1234);
  }
}