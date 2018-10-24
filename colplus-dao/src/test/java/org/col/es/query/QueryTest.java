package org.col.es.query;

import org.junit.Test;

public class QueryTest {

  @Test
  public void testSerializeQuery1() {
    EsSearchRequest sr = new EsSearchRequest();
    ConstantScoreQuery csq = new ConstantScoreQuery(new TermQuery("foo", "bar", 8.1f));
    sr.setQuery(csq);
    sr.addAggregation("collection-type", new TermsAggregation("collectionType",10));
    sr.addAggregation("record-basis", new TermsAggregation("recordBasis",10));
    System.out.println(sr);
  }

}
