package org.col.es.query;

import java.util.List;

import org.col.es.EsReadTestBase;
import org.col.es.dsl.CaseInsensitivePrefixQuery;
import org.col.es.dsl.PrefixQuery;
import org.col.es.dsl.Query;
import org.col.es.model.NameUsageDocument;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrefixQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    NameUsageDocument doc = new NameUsageDocument();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed using the IGNORE_CASE analyzer.
    doc.setAuthorship(s);
    indexRaw(doc);
    Query query = new CaseInsensitivePrefixQuery("authorship", s.substring(0, 8));
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test2() {
    NameUsageDocument doc = new NameUsageDocument();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed as-is.
    doc.setUsageId(s);
    indexRaw(doc);
    Query query = new PrefixQuery("usageId", s);
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(1, result.size());
  }


  @Test
  public void test3() {
    NameUsageDocument doc = new NameUsageDocument();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed as-is.
    doc.setUsageId(s);
    indexRaw(doc);
    Query query = new PrefixQuery("usageId", s.substring(0, 10));
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(1, result.size());
  }

}
