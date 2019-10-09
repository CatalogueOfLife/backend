package org.col.es.name.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.col.api.model.Page;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SortBy;
import org.col.es.EsReadTestBase;
import org.col.es.model.NameUsageDocument;
import org.col.es.query.EsSearchRequest;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

  @Test
  public void testSortTaxonomic_01() {

    NameUsageDocument kingdomF = new NameUsageDocument();
    kingdomF.setRank(Rank.KINGDOM);
    kingdomF.setScientificName("F");

    NameUsageDocument kingdomP = new NameUsageDocument();
    kingdomP.setRank(Rank.KINGDOM);
    kingdomP.setScientificName("P");

    NameUsageDocument phylumC = new NameUsageDocument();
    phylumC.setRank(Rank.PHYLUM);
    phylumC.setScientificName("C");

    NameUsageDocument phylumD = new NameUsageDocument();
    phylumD.setRank(Rank.PHYLUM);
    phylumD.setScientificName("D");

    NameUsageDocument classZ = new NameUsageDocument();
    classZ.setRank(Rank.CLASS);
    classZ.setScientificName("Z");

    NameUsageDocument orderA = new NameUsageDocument();
    orderA.setRank(Rank.ORDER);
    orderA.setScientificName("A");

    NameUsageDocument familyT = new NameUsageDocument();
    familyT.setRank(Rank.FAMILY);
    familyT.setScientificName("F");

    NameUsageDocument genusA = new NameUsageDocument();
    genusA.setRank(Rank.GENUS);
    genusA.setScientificName("A");

    NameUsageDocument genusC = new NameUsageDocument();
    genusC.setRank(Rank.GENUS);
    genusC.setScientificName("C");

    NameUsageDocument speciesZ = new NameUsageDocument();
    speciesZ.setRank(Rank.SPECIES);
    speciesZ.setScientificName("Z");

    List<NameUsageDocument> expected = Arrays.asList(
        kingdomF,
        kingdomP,
        phylumC,
        phylumD,
        classZ,
        orderA,
        familyT,
        genusA,
        genusC,
        speciesZ);

    List<NameUsageDocument> docs = new ArrayList<NameUsageDocument>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.TAXONOMIC);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();

    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(expected, result);

    // Let's just do this one more time.
    truncate();
    docs = new ArrayList<NameUsageDocument>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    result = queryRaw(esQuery);
    assertEquals(expected, result);

  }

  @Test
  public void testSortTaxonomicDescending_01() {

    NameUsageDocument kingdomF = new NameUsageDocument();
    kingdomF.setRank(Rank.KINGDOM);
    kingdomF.setScientificName("F");

    NameUsageDocument kingdomP = new NameUsageDocument();
    kingdomP.setRank(Rank.KINGDOM);
    kingdomP.setScientificName("P");

    NameUsageDocument phylumC = new NameUsageDocument();
    phylumC.setRank(Rank.PHYLUM);
    phylumC.setScientificName("C");

    NameUsageDocument phylumD = new NameUsageDocument();
    phylumD.setRank(Rank.PHYLUM);
    phylumD.setScientificName("D");

    NameUsageDocument classZ = new NameUsageDocument();
    classZ.setRank(Rank.CLASS);
    classZ.setScientificName("Z");

    NameUsageDocument orderA = new NameUsageDocument();
    orderA.setRank(Rank.ORDER);
    orderA.setScientificName("A");

    NameUsageDocument familyT = new NameUsageDocument();
    familyT.setRank(Rank.FAMILY);
    familyT.setScientificName("F");

    NameUsageDocument genusA = new NameUsageDocument();
    genusA.setRank(Rank.GENUS);
    genusA.setScientificName("A");

    NameUsageDocument genusC = new NameUsageDocument();
    genusC.setRank(Rank.GENUS);
    genusC.setScientificName("C");

    NameUsageDocument speciesZ = new NameUsageDocument();
    speciesZ.setRank(Rank.SPECIES);
    speciesZ.setScientificName("Z");

    // Rank reversed, but name still alphabetical
    List<NameUsageDocument> expected = Arrays.asList(
        speciesZ,
        genusA,
        genusC,
        familyT,
        orderA,
        classZ,
        phylumC,
        phylumD,
        kingdomF,
        kingdomP);

    List<NameUsageDocument> docs = new ArrayList<NameUsageDocument>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    NameSearchRequest query = new NameSearchRequest();
    query.setSortBy(SortBy.TAXONOMIC);
    query.setReverse(true);
    EsSearchRequest esQuery = new RequestTranslator(query, new Page()).translate();

    List<NameUsageDocument> result = queryRaw(esQuery);
    assertEquals(expected, result);

    // Let's just do this one more time.
    truncate();
    docs = new ArrayList<NameUsageDocument>(expected);
    Collections.shuffle(docs);
    indexRaw(docs);

    result = queryRaw(esQuery);
    assertEquals(expected, result);

  }

}
