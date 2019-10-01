package org.col.es.name.search;

import java.util.Arrays;
import java.util.List;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.es.EsReadTestBase;
import org.col.es.model.NameUsageDocument;
import org.col.es.query.EsSearchRequest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

// @Ignore
public class SortingTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void testSortByName_01() {
    NameUsageDocument docA = new NameUsageDocument();
    docA.setScientificName("A");
    NameUsageDocument docB = new NameUsageDocument();
    docB.setScientificName("B");
    NameUsageDocument docC = new NameUsageDocument();
    docC.setScientificName("C");
    NameUsageDocument docD = new NameUsageDocument();
    docD.setScientificName("D");
    NameUsageDocument docE = new NameUsageDocument();
    docE.setScientificName("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<NameUsageDocument> expected = Arrays.asList(docA, docB, docC, docD, docE);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.NAME);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();
    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }

  @Test
  public void testSortByNameDescending_01() {
    NameUsageDocument docA = new NameUsageDocument();
    docA.setScientificName("A");
    NameUsageDocument docB = new NameUsageDocument();
    docB.setScientificName("B");
    NameUsageDocument docC = new NameUsageDocument();
    docC.setScientificName("C");
    NameUsageDocument docD = new NameUsageDocument();
    docD.setScientificName("D");
    NameUsageDocument docE = new NameUsageDocument();
    docE.setScientificName("E");

    indexRaw(docB, docA, docD, docE, docC);

    List<NameUsageDocument> expected = Arrays.asList(docE, docD, docC, docB, docA);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.NAME);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();
    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(expected, result);
  }

  @Test
  public void testSortNative_01() {
    NameUsageDocument docA = new NameUsageDocument();
    docA.setScientificName("A");
    NameUsageDocument docB = new NameUsageDocument();
    docB.setScientificName("B");
    NameUsageDocument docC = new NameUsageDocument();
    docC.setScientificName("C");
    NameUsageDocument docD = new NameUsageDocument();
    docD.setScientificName("D");
    NameUsageDocument docE = new NameUsageDocument();
    docE.setScientificName("E");

    List<NameUsageDocument> docs = Arrays.asList(docB, docA, docD, docE, docC);

    indexRaw(docs);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(null);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();
    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(docs, result);
  }

  @Test
  public void testSortNative_02() {
    NameUsageDocument docA = new NameUsageDocument();
    docA.setScientificName("A");
    NameUsageDocument docB = new NameUsageDocument();
    docB.setScientificName("B");
    NameUsageDocument docC = new NameUsageDocument();
    docC.setScientificName("C");
    NameUsageDocument docD = new NameUsageDocument();
    docD.setScientificName("D");
    NameUsageDocument docE = new NameUsageDocument();
    docE.setScientificName("E");

    List<NameUsageDocument> docs = Arrays.asList(docB, docA, docD, docE, docC);

    indexRaw(docs);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.NATIVE);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();
    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(docs, result);
  }
  
}
