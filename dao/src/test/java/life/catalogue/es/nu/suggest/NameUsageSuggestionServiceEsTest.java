package life.catalogue.es.nu.suggest;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;

import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameUsageSuggestionServiceEsTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test // The basics
  public void test01() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("abcde");
    query.setFuzzy(true);

    Name n = new Name();
    n.setDatasetKey(1);
    n.setRank(Rank.SPECIES);
    n.setId("1");
    n.setSpecificEpithet("AbCdEfGhIjK");
    EsNameUsage doc1 = newDocument(n); // match 1

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SUBSPECIES);
    n.setSpecificEpithet("AbCdEfG"); // Just for peeking at scores
    EsNameUsage doc2 = newDocument(n); // match 2

    n = new Name();
    n.setDatasetKey(1);
    n.setId("3");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("   AbCdE  ");
    EsNameUsage doc3 = newDocument(n); // match 3

    n = new Name();
    n.setDatasetKey(1);
    n.setId("4");
    n.setRank(Rank.SUBSPECIES);
    n.setSpecificEpithet("AbCd");
    EsNameUsage doc4 = newDocument(n); // no match (name)

    n = new Name();
    n.setDatasetKey(1234567);
    n.setId("5");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("abcde");
    EsNameUsage doc5 = newDocument(n); // no match (dataset key)

    n = new Name();
    n.setDatasetKey(1);
    n.setId("6");
    n.setRank(Rank.FAMILY);
    n.setSpecificEpithet("abcde");
    EsNameUsage doc6 = newDocument(n); // match 4

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameUsageSuggestResponse response = suggest(query);

    assertTrue(containsUsageIds(response, doc1, doc2, doc3, doc6));
  }

  @Test // Relevance goes from infraspecific epithet <- specific epithet <- genus
  public void test02() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("abcde");
    query.setFuzzy(false);

    String THE_NAME = "AbCdEfGhIjK";

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.GENUS);
    n.setUninomial(THE_NAME);
    n.rebuildScientificName();
    EsNameUsage doc1 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SPECIES);
    n.setGenus("Abies");
    n.setSpecificEpithet(THE_NAME);
    n.rebuildScientificName();
    EsNameUsage doc2 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("3");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setRank(Rank.SUBSPECIES);
    n.setInfraspecificEpithet(THE_NAME);
    n.rebuildScientificName();
    EsNameUsage doc3 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("4");
    n.setRank(Rank.SUBSPECIES);
    EsNameUsage doc4 = newDocument(n);

    indexRaw(doc1, doc2, doc3, doc4);

    query.setSortBy(NameUsageSearchRequest.SortBy.TAXONOMIC);
    NameUsageSuggestResponse response = suggest(query);
    assertEquals(3, response.getSuggestions().size());
    assertEquals("1", response.getSuggestions().get(0).getUsageId());
    assertEquals("2", response.getSuggestions().get(1).getUsageId());
    assertEquals("3", response.getSuggestions().get(2).getUsageId());

    query.setSortBy(NameUsageSearchRequest.SortBy.RELEVANCE);
    response = suggest(query);
    assertEquals(3, response.getSuggestions().size());
    assertEquals("1", response.getSuggestions().get(0).getUsageId());
    assertEquals("2", response.getSuggestions().get(1).getUsageId());
    assertEquals("3", response.getSuggestions().get(2).getUsageId());

  }

  @Test // Relevance goes from infraspecific epithet -> specific epithet -> genus
  public void test02b() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("abcde fghij");

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    n.setGenus("abcde");
    n.setSpecificEpithet("fghij");
    EsNameUsage doc1 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SUBSPECIES);
    n.setGenus("abcde");
    n.setInfraspecificEpithet("fghij");
    EsNameUsage doc2 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("3");
    n.setRank(Rank.SUBSPECIES);
    n.setSpecificEpithet("abcde");
    n.setInfraspecificEpithet("fghij");
    EsNameUsage doc3 = newDocument(n);

    n = new Name();
    n.setId("4");
    n.setDatasetKey(1);
    n.setRank(Rank.SPECIES);
    EsNameUsage doc4 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("5");
    n.setRank(Rank.SPECIES);
    EsNameUsage doc5 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("6");
    n.setRank(Rank.SPECIES);
    n.setScientificName("abcde fghij");
    EsNameUsage doc6 = newDocument(n);

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameUsageSuggestResponse response = suggest(query);

    response.getSuggestions().forEach(s -> System.out.println("usage ID " + s.getUsageId() + ": " + s.getScore()));

  }

  @Test // lots of search terms
  public void test03() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("LARUS FUSCUS FUSCUS (LINNAEUS 1752)");
    query.setFuzzy(true);

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    n.setScientificName("Larus fuscus");
    EsNameUsage doc1 = newDocument(n);

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SPECIES);
    EsNameUsage doc2 = newDocument(n);

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
    t.setStatus(TaxonomicStatus.ACCEPTED);

    NameUsageWrapper nuw = new NameUsageWrapper(t);

    index(nuw);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);

    NameUsageSuggestResponse response;

    query.setQ("Larus argentatus argenteus");
    response = suggest(query);
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
  public void testTruncateNotFuzzy() {

    // query
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setFuzzy(false);

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    // genus has 13 chars - more than the 12 max ngram limit - but it gets transformed into Tiranosaura
    // which is smaller
    n.setScientificName("Tyrannosaurus rex");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    n.setRank(Rank.SPECIES);
    EsNameUsage doc1 = newDocument(n, TaxonomicStatus.ACCEPTED); // match 1

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SUBSPECIES);
    // subspecies has more than the 12 max ngram limit
    n.setScientificName("Tyrannosaurus rex altobrasiliensis");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    n.setInfraspecificEpithet("altobrasiliensis");
    EsNameUsage doc2 = newDocument(n); // match 2

    n = new Name();
    n.setDatasetKey(1);
    n.setId("3");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("   AbCdE  ");
    EsNameUsage doc3 = newDocument(n); // match 3

    n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("AbCdEfGhIjK");
    EsNameUsage doc4 = newDocument(n); // match 1

    indexRaw(doc1, doc2, doc3, doc4);

    query.setQ("abcde");

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
    assertMatch(response, doc2);
  }

  @Test
  public void testTruncateFuzzy() {

    // query
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setFuzzy(true);

    Name n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    // genus has 13 chars - more than the 12 max ngram limit - but it gets transformed into Tiranosaura
    // which is smaller
    n.setScientificName("Tyrannosaurus rex");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    EsNameUsage doc1 = newDocument(n); // match 1

    n = new Name();
    n.setDatasetKey(1);
    n.setId("2");
    n.setRank(Rank.SUBSPECIES);
    // subspecies has more than the 12 max ngram limit
    n.setScientificName("Tyrannosaurus rex altobrasiliensis");
    n.setGenus("Tyrannosaurus");
    n.setSpecificEpithet("rex");
    n.setInfraspecificEpithet("altobrasiliensis");
    n.setRank(Rank.SUBSPECIES);
    EsNameUsage doc2 = newDocument(n); // match 2

    n = new Name();
    n.setDatasetKey(1);
    n.setId("3");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("   AbCdE  ");
    EsNameUsage doc3 = newDocument(n); // match 3

    n = new Name();
    n.setDatasetKey(1);
    n.setId("1");
    n.setRank(Rank.SPECIES);
    n.setSpecificEpithet("AbCdEfGhIjK");
    EsNameUsage doc4 = newDocument(n); // match 1

    indexRaw(doc1, doc2, doc3, doc4);

    query.setQ("abcde");

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
    assertMatch(response, doc2);
  }

  @Test
  public void testParentTaxon() {
    Name n = new Name();
    n.setId("1");
    n.setDatasetKey(1);
    n.setScientificName("Larus fuscus");
    n.setGenus("Larus");
    n.setSpecificEpithet("fuscus");
    n.setRank(Rank.SPECIES);
    EsNameUsage nu1 = newDocument(n, TaxonomicStatus.ACCEPTED, "Laridae", "Larus", "Larus fuscus");

    n = new Name();
    n.setId("2");
    n.setDatasetKey(1);
    n.setScientificName("Larus fuscus argentatus");
    n.setGenus("Larus");
    n.setSpecificEpithet("fuscus");
    n.setInfraspecificEpithet("argentatus");
    n.setRank(Rank.SUBSPECIES);
    EsNameUsage nu2 = newDocument(n, TaxonomicStatus.ACCEPTED, "Laridae", "Larus", "Larus fuscus", "Larus fuscus argentatus");

    n = new Name();
    n.setId("3");
    n.setDatasetKey(1);
    n.setScientificName("Larus");
    n.setUninomial("Larus");
    n.setRank(Rank.GENUS);
    EsNameUsage nu3 = newDocument(n, TaxonomicStatus.ACCEPTED, "Laridae", "Larus");

    n = new Name();
    n.setId("4");
    n.setDatasetKey(1);
    n.setScientificName("Meles meles");
    n.setGenus("Meles");
    n.setSpecificEpithet("meles");
    n.setRank(Rank.SPECIES);
    EsNameUsage nu4 = newDocument(n, TaxonomicStatus.ACCEPTED, "Mustelidae", "Meles", "Meles meles");

    indexRaw(nu1, nu2, nu3, nu4);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("larus f");
    NameUsageSuggestResponse response = suggest(query);

    assertMatch(response, nu1, nu2);

  }

  @Test
  public void testParentTaxon2() {
    Name n = new Name();
    n.setId("1");
    n.setDatasetKey(1);
    n.setScientificName("Larus fuscus");
    n.setGenus("Larus");
    n.setSpecificEpithet("fuscus");
    n.setRank(Rank.SPECIES);
    EsNameUsage nu1 = newDocument(n, TaxonomicStatus.ACCEPTED, "Laridae", "Larus", "Larus fuscus");

    indexRaw(nu1);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("larus f");
    NameUsageSuggestResponse response = suggest(query);

    assertEquals("Larus fuscus", response.getSuggestions().get(0).getMatch());
    assertEquals("Larus", response.getSuggestions().get(0).getContext());
    assertEquals("Larus fuscus (Larus)", response.getSuggestions().get(0).getSuggestion());

  }

  @Test
  public void testParentTaxon3() {
    Name n = new Name();
    n.setId("1");
    n.setDatasetKey(1);
    n.setScientificName("Mustelidae");
    n.setUninomial("Mustelidae");
    n.setRank(Rank.FAMILY);
    EsNameUsage nu1 = newDocument(n, TaxonomicStatus.ACCEPTED, "Mammalia", "Carnivora", "Mustelidae");

    indexRaw(nu1);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("mus");
    NameUsageSuggestResponse response = suggest(query);

    assertEquals("Mustelidae", response.getSuggestions().get(0).getMatch());
    assertEquals("Carnivora", response.getSuggestions().get(0).getContext());
    assertEquals("Mustelidae (Carnivora)", response.getSuggestions().get(0).getSuggestion());

  }

  @Test
  public void testSynonym() {
    Name n = new Name();
    n.setId("1");
    n.setDatasetKey(1);
    n.setScientificName("Larus foo");
    n.setGenus("Larus");
    n.setRank(Rank.SPECIES);

    EsNameUsage nu1 = newDocument(n, TaxonomicStatus.SYNONYM, "Laridae", "Larus", "fuscus", "foo");
    nu1.setClassificationIds(Arrays.asList("1", "2", "3", "4"));
    nu1.setAcceptedName("Larus fuscus");

    indexRaw(nu1);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setQ("foo");
    NameUsageSuggestResponse response = suggest(query);

    assertEquals("Larus foo", response.getSuggestions().get(0).getMatch());
    assertEquals("Larus foo (synonym of Larus fuscus)", response.getSuggestions().get(0).getSuggestion());
    assertEquals("3", response.getSuggestions().get(0).getAcceptedUsageId());
  }

  @Test
  public void testAcceptedOnly() {

    Name n = new Name();
    n.setId("1");
    n.setDatasetKey(1);
    n.setScientificName("Larus fuscus");
    n.setGenus("Larus");
    n.setSpecificEpithet("fuscus");
    n.setRank(Rank.SPECIES);
    EsNameUsage nu1 = newDocument(n, TaxonomicStatus.ACCEPTED, "Laridae", "Larus", "Larus fuscus");

    n = new Name();
    n.setId("2");
    n.setDatasetKey(1);
    n.setScientificName("Larus fuscus");
    n.setGenus("Larus");
    n.setSpecificEpithet("fuscus");
    n.setRank(Rank.SPECIES);
    EsNameUsage nu2 = newDocument(n, TaxonomicStatus.SYNONYM, "Laridae", "Larus", "Larus fuscus");

    indexRaw(nu1, nu2);

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(1);
    query.setAccepted(true);
    query.setQ("laru");
    NameUsageSuggestResponse response = suggest(query);

    assertEquals(1, response.getSuggestions().size());
    assertEquals("1", response.getSuggestions().get(0).getUsageId());

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
