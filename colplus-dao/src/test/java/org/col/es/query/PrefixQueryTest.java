package org.col.es.query;

import java.util.List;

import org.col.es.EsReadTestBase;
import org.col.es.dsl.PrefixQuery;
import org.col.es.dsl.Query;
import org.col.es.model.NameStrings;
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
    NameStrings strings = new NameStrings();
    String s = "  this  is  a  long  string  with  spaces";
    strings.setScientificNameWN(s);
    doc.setNameStrings(strings);
    indexRaw(doc);
    Query query = new PrefixQuery("nameStrings.scientificNameLC", s.substring(0, 5));
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test2() {
    NameUsageDocument doc = new NameUsageDocument();
    NameStrings strings = new NameStrings();
    String s = "  this  is  a  long  string  with  spaces";
    strings.setScientificNameWN(s);
    doc.setNameStrings(strings);
    indexRaw(doc);
    Query query = new PrefixQuery("nameStrings.scientificNameLC", s);
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test3() {
    NameUsageDocument doc = new NameUsageDocument();
    NameStrings strings = new NameStrings();
    String s = "  this  is  a  long  string  with  spaces";
    strings.setScientificNameWN(s);
    doc.setNameStrings(strings);
    indexRaw(doc);
    Query query = new PrefixQuery("nameStrings.scientificNameLC", s.substring(1, 5));
    List<NameUsageDocument> result = queryRaw(query);
    assertEquals(0, result.size());
  }

}
