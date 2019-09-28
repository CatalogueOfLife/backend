package org.col.es.name.suggest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.col.api.model.Name;
import org.col.api.search.NameSuggestRequest;
import org.col.api.search.NameSuggestResponse;
import org.col.api.search.NameSuggestion;
import org.col.es.EsReadTestBase;
import org.col.es.model.NameStrings;
import org.col.es.model.NameUsageDocument;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;

import static java.util.stream.Collectors.*;

import static org.junit.Assert.*;

public class NameSuggestionServiceTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test01() {

    NameSuggestRequest query = new NameSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde");

    NameUsageDocument doc1 = new NameUsageDocument(); // match 1
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setSpecificEpithet("AbCdEfGhIjK");
    doc1.setNameStrings(new NameStrings(n));

    NameUsageDocument doc2 = new NameUsageDocument(); // match 2
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("AbCdEfG"); // Just for peeking at scores
    doc2.setNameStrings(new NameStrings(n));

    NameUsageDocument doc3 = new NameUsageDocument(); // match 3
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("   AbCdE  ");
    doc3.setNameStrings(new NameStrings(n));

    NameUsageDocument doc4 = new NameUsageDocument(); // no match (name)
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("AbCd");
    doc4.setNameStrings(new NameStrings(n));

    NameUsageDocument doc5 = new NameUsageDocument(); // no match (dataset key)
    doc5.setDatasetKey(1234567);
    doc5.setUsageId("5");
    doc5.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("abcde");
    doc5.setNameStrings(new NameStrings(n));

    NameUsageDocument doc6 = new NameUsageDocument(); // no match (rank)
    doc5.setDatasetKey(1);
    doc5.setUsageId("6");
    doc5.setRank(Rank.FAMILY);
    n = new Name();
    n.setSpecificEpithet("abcde");
    doc5.setNameStrings(new NameStrings(n));

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameSuggestResponse response = suggest(query);

    assertTrue(containsUsageIds(response, doc1, doc2, doc3));

  }

  private static boolean containsUsageIds(NameSuggestResponse response, NameUsageDocument... docs) {
    Set<String> expected = Arrays.stream(docs).map(NameUsageDocument::getUsageId).collect(toSet());
    Set<String> actual = response.getSuggestions().stream().map(NameSuggestion::getUsageId).collect(toSet());
    return expected.equals(actual);
  }

}
