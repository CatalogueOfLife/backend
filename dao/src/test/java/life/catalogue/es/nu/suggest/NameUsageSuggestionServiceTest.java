package life.catalogue.es.nu.suggest;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.NameStrings;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameUsageSuggestionServiceTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test // The basics
  public void test01() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde");
    query.setFuzzy(true);

    EsNameUsage doc1 = new EsNameUsage(); // match 1
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setSpecificEpithet("AbCdEfGhIjK");
    doc1.setNameStrings(new NameStrings(n));

    EsNameUsage doc2 = new EsNameUsage(); // match 2
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("AbCdEfG"); // Just for peeking at scores
    doc2.setNameStrings(new NameStrings(n));

    EsNameUsage doc3 = new EsNameUsage(); // match 3
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("   AbCdE  ");
    doc3.setNameStrings(new NameStrings(n));

    EsNameUsage doc4 = new EsNameUsage(); // no match (name)
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("AbCd");
    doc4.setNameStrings(new NameStrings(n));

    EsNameUsage doc5 = new EsNameUsage(); // no match (dataset key)
    doc5.setDatasetKey(1234567);
    doc5.setUsageId("5");
    doc5.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("abcde");
    doc5.setNameStrings(new NameStrings(n));

    EsNameUsage doc6 = new EsNameUsage(); // match 4
    doc6.setDatasetKey(1);
    doc6.setUsageId("6");
    doc6.setRank(Rank.FAMILY);
    n = new Name();
    n.setSpecificEpithet("abcde");
    doc6.setNameStrings(new NameStrings(n));

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameUsageSuggestResponse response = suggest(query);

    assertTrue(containsUsageIds(response, doc1, doc2, doc3, doc6));
  }

  @Test // Relevance goes from infraspecific epithet -> specific epithet -> genus
  public void test02() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde");
    query.setVernaculars(true);
    query.setFuzzy(true);

    String THE_NAME = "AbCdEfGhIjK";

    EsNameUsage doc1 = new EsNameUsage();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setGenus(THE_NAME); // 3: genus
    doc1.setNameStrings(new NameStrings(n));

    EsNameUsage doc2 = new EsNameUsage();
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SPECIES); // 2: species
    n = new Name();
    n.setSpecificEpithet(THE_NAME);
    doc2.setNameStrings(new NameStrings(n));

    EsNameUsage doc3 = new EsNameUsage();
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SUBSPECIES); // 1: subspecies
    n = new Name();
    n.setInfraspecificEpithet(THE_NAME);
    doc3.setNameStrings(new NameStrings(n));

    EsNameUsage doc4 = new EsNameUsage();
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SUBSPECIES);
    doc4.setVernacularNames(Arrays.asList(THE_NAME)); // 4: vernacular

    indexRaw(doc1, doc2, doc3, doc4);

    NameUsageSuggestResponse response = suggest(query);

    assertEquals("3", response.getSuggestions().get(0).getUsageId());
    assertEquals("2", response.getSuggestions().get(1).getUsageId());
    assertEquals("1", response.getSuggestions().get(2).getUsageId());
    assertEquals("4", response.getSuggestions().get(3).getUsageId());

  }

  @Test // Relevance goes from infraspecific epithet -> specific epithet -> genus
  public void test02b() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde fghij");
    query.setVernaculars(true);

    EsNameUsage doc1 = new EsNameUsage();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setGenus("abcde");
    n.setSpecificEpithet("fghij");
    doc1.setNameStrings(new NameStrings(n));

    EsNameUsage doc2 = new EsNameUsage();
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setGenus("abcde");
    n.setInfraspecificEpithet("fghij");
    doc2.setNameStrings(new NameStrings(n));

    EsNameUsage doc3 = new EsNameUsage();
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("abcde");
    n.setInfraspecificEpithet("fghij");
    doc3.setNameStrings(new NameStrings(n));

    EsNameUsage doc4 = new EsNameUsage();
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SPECIES);
    doc4.setVernacularNames(Arrays.asList("abcde fghij"));

    EsNameUsage doc5 = new EsNameUsage();
    doc5.setDatasetKey(1);
    doc5.setUsageId("5");
    doc5.setRank(Rank.SPECIES);
    doc5.setVernacularNames(Arrays.asList("abcde", "fghij"));

    EsNameUsage doc6 = new EsNameUsage();
    doc6.setDatasetKey(1);
    doc6.setUsageId("6");
    doc6.setRank(Rank.SPECIES);
    doc6.setScientificName("abcde fghij");

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameUsageSuggestResponse response = suggest(query);

    response.getSuggestions().stream().forEach(s -> System.out.println("usage ID " + s.getUsageId() + ": " + s.getScore()));

  }

  @Test // lots of search terms
  public void test03() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("LARUS FUSCUS FUSCUS (LINNAEUS 1752)");
    query.setVernaculars(true);
    query.setFuzzy(true);

    EsNameUsage doc1 = new EsNameUsage();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    doc1.setScientificName("Larus fuscus");

    EsNameUsage doc2 = new EsNameUsage();
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SPECIES);
    doc2.setVernacularNames(Arrays.asList("Foo Bar Larusca"));

    indexRaw(doc1, doc2);

    NameUsageSuggestResponse response = suggest(query);

    // We have switched from OR-ing the search terms to AND-ing the search terms
    // assertEquals(2, response.getSuggestions().size());
    assertEquals(0, response.getSuggestions().size());

  }

  @Test
  public void test04() {

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SUBSPECIES);
    n.setGenus("Larus");
    n.setSpecificEpithet("argentatus");
    n.setInfraspecificEpithet("argenteus");
    n.setScientificName("Larus argentatus argenteus");

    Taxon t = new Taxon();
    t.setId("1");
    t.setDatasetKey(1);
    t.setName(n);

    NameUsageWrapper nuw = new NameUsageWrapper(t);

    index(nuw);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);

    query.setQ("Larus argentatus argenteus");
    NameUsageSuggestResponse response = suggest(query);
    float score1 = response.getSuggestions().get(0).getScore();

    // User mixed up specific and infraspecific epithet (still good score)
    query.setQ("Larus argenteus argentatus");
    response = suggest(query);
    float score2 = response.getSuggestions().get(0).getScore();

    // This should actually score higher than the binomial (Larus argentatus)
    query.setQ("Larus argenteus");
    response = suggest(query);
    float score3 = response.getSuggestions().get(0).getScore();

    query.setQ("Larus argentatus");
    response = suggest(query);
    float score4 = response.getSuggestions().get(0).getScore();

    query.setQ("argentatus L.");
    response = suggest(query);
    float score5 = response.getSuggestions().get(0).getScore();

    query.setQ("argenteus");
    response = suggest(query);
    float score6 = response.getSuggestions().get(0).getScore();

    query.setQ("Larus argentatus arg");
    response = suggest(query);
    float score7 = response.getSuggestions().get(0).getScore();

    System.out.println("score1: " + score1); // just curious
    System.out.println("score2: " + score2);
    System.out.println("score3: " + score3);
    System.out.println("score4: " + score4);
    System.out.println("score5: " + score5);
    System.out.println("score6: " + score6);
    System.out.println("score7: " + score7);

    // Since we now issue disjunction_max queries, there's just no predicting scores any longer.
    // assertTrue(score1 > score2);
    // assertTrue(score2 > score3);
    // assertTrue(score3 > score4);
    // assertTrue(score4 > score5);
    // assertTrue(score5 > score6);
  }

  /**
   * Suggest with a query larger than ngram max should still match
   */
  @Test
  @Ignore("Suggest not working as expected yet")
  public void testTruncate() {
    EsNameUsage doc1 = new EsNameUsage(); // match 1
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    // genus has 13 chars - more than the 12 max ngram limit - but it gets transformed into Tiranosaura which is smaller
    n.setScientificName("Tyrannosaurus rex");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    n.setRank(Rank.SPECIES);
    doc1.setNameStrings(new NameStrings(n));

    EsNameUsage doc2 = new EsNameUsage(); // match 2
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SUBSPECIES);
    n = new Name();
    // subspecies has more than the 12 max ngram limit
    n.setScientificName("Tyrannosaurus rex altobrasiliensis");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    n.setInfraspecificEpithet("altobrasiliensis");
    n.setRank(Rank.SUBSPECIES);
    doc2.setNameStrings(new NameStrings(n));

    EsNameUsage doc3 = new EsNameUsage(); // match 3
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("   AbCdE  ");
    doc3.setNameStrings(new NameStrings(n));

    EsNameUsage doc4 = new EsNameUsage(); // match 1
    doc4.setDatasetKey(1);
    doc4.setUsageId("1");
    doc4.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet("AbCdEfGhIjK");
    doc4.setNameStrings(new NameStrings(n));

    indexRaw(doc1, doc2, doc3, doc4);

    // query
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde");
    query.setFuzzy(true);

    NameUsageSuggestResponse response = suggest(query);
    assertMatch(response, doc3, doc4);

    query.setQ("rex");
    response = suggest(query);
    assertMatch(response, doc1, doc2);

    query.setQ("Tyran");
    response = suggest(query);
    assertMatch(response, doc1, doc2);

    query.setQ("Tyrannosaurus");
    response = suggest(query);
    assertMatch(response, doc1, doc2);

    query.setQ("Tyrannosaurus re");
    response = suggest(query);
    assertMatch(response, doc1, doc2);

    query.setQ("altobrasiliensis");
    response = suggest(query);
    assertMatch(response, doc1, doc2);
  }

  private static boolean containsUsageIds(NameUsageSuggestResponse response, EsNameUsage... docs) {
    Set<String> expected = Arrays.stream(docs).map(EsNameUsage::getUsageId).collect(toSet());
    Set<String> actual = response.getSuggestions().stream().map(NameUsageSuggestion::getUsageId).collect(toSet());
    return expected.equals(actual);
  }

  private static void assertMatch(NameUsageSuggestResponse response, EsNameUsage... docs) {
    Set<String> expected = Arrays.stream(docs).map(EsNameUsage::getUsageId).collect(toSet());
    Set<String> actual = response.getSuggestions().stream().map(NameUsageSuggestion::getUsageId).collect(toSet());
    assertTrue(expected.equals(actual));
  }
}
