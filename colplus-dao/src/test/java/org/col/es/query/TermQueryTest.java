package org.col.es.query;

import java.util.List;

import org.col.es.EsReadTestBase;
import org.col.es.model.NameUsageDocument;
import org.col.es.query.TermQuery;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TermQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    NameUsageDocument doc1 = new NameUsageDocument();
    doc1.setDatasetKey(1);
    NameUsageDocument doc2 = new NameUsageDocument();
    doc2.setDatasetKey(2);
    NameUsageDocument doc3 = new NameUsageDocument();
    doc3.setDatasetKey(3);
    NameUsageDocument doc4 = new NameUsageDocument();
    doc4.setDatasetKey(3);
    NameUsageDocument doc5 = new NameUsageDocument();
    doc5.setDatasetKey(3);
    NameUsageDocument doc6 = new NameUsageDocument();
    doc6.setDatasetKey(6);
    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);
    List<NameUsageDocument> result = queryRaw(new TermQuery("datasetKey", 3));
    assertEquals(3, result.size());
  }

}
