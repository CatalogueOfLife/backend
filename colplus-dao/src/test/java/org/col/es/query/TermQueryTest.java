package org.col.es.query;

import org.junit.Test;


public class TermQueryTest {
  
  @Test
  public void test() {
    EsSearchRequest sr = new EsSearchRequest();
    sr.setQuery(new TermQuery("foo", 1));
    System.out.println(sr.toString());
  }

}
