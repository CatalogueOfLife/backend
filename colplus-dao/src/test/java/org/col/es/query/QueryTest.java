package org.col.es.query;

import org.junit.Test;

public class QueryTest {

  @Test
  public void testSerializeQuery1() {
    ConstantScoreQuery csq = new ConstantScoreQuery(new Filter(new Term("foo", "bar",8.1f)));
    System.out.println(csq.toString());
  }

}
