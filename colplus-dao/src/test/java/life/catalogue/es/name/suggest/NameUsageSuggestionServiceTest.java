package life.catalogue.es.name.suggest;

import java.util.Arrays;
import java.util.Set;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.api.search.NameUsageSuggestion;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.model.NameStrings;
import life.catalogue.es.model.NameUsageDocument;
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

    NameUsageDocument doc6 = new NameUsageDocument(); // no match (rank must be species or lower)
    doc5.setDatasetKey(1);
    doc5.setUsageId("6");
    doc5.setRank(Rank.FAMILY);
    n = new Name();
    n.setSpecificEpithet("abcde");
    doc5.setNameStrings(new NameStrings(n));

    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    NameUsageSuggestResponse response = suggest(query);

    assertTrue(containsUsageIds(response, doc1, doc2, doc3));

  }

  @Test // Relevance goes from infraspecific epithet -> specific epithet -> genus
  public void test02() {

    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(1);
    query.setQ("abcde");
    query.setVernaculars(true);

    String THE_NAME = "AbCdEfGhIjK";

    NameUsageDocument doc1 = new NameUsageDocument();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setGenus(THE_NAME);
    doc1.setNameStrings(new NameStrings(n));

    NameUsageDocument doc2 = new NameUsageDocument();
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SPECIES);
    n = new Name();
    n.setSpecificEpithet(THE_NAME);
    doc2.setNameStrings(new NameStrings(n));

    NameUsageDocument doc3 = new NameUsageDocument();
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setInfraspecificEpithet(THE_NAME);
    doc3.setNameStrings(new NameStrings(n));

    NameUsageDocument doc4 = new NameUsageDocument();
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SUBSPECIES);
    doc4.setVernacularNames(Arrays.asList(THE_NAME));

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

    NameUsageDocument doc1 = new NameUsageDocument();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    Name n = new Name();
    n.setGenus("abcde");
    n.setSpecificEpithet("fghij");
    doc1.setNameStrings(new NameStrings(n));

    NameUsageDocument doc2 = new NameUsageDocument();
    doc2.setDatasetKey(1);
    doc2.setUsageId("2");
    doc2.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setGenus("abcde");
    n.setInfraspecificEpithet("fghij");
    doc2.setNameStrings(new NameStrings(n));

    NameUsageDocument doc3 = new NameUsageDocument();
    doc3.setDatasetKey(1);
    doc3.setUsageId("3");
    doc3.setRank(Rank.SUBSPECIES);
    n = new Name();
    n.setSpecificEpithet("abcde");
    n.setInfraspecificEpithet("fghij");
    doc3.setNameStrings(new NameStrings(n));

    NameUsageDocument doc4 = new NameUsageDocument();
    doc4.setDatasetKey(1);
    doc4.setUsageId("4");
    doc4.setRank(Rank.SPECIES);
    doc4.setVernacularNames(Arrays.asList("abcde fghij"));

    NameUsageDocument doc5 = new NameUsageDocument();
    doc5.setDatasetKey(1);
    doc5.setUsageId("5");
    doc5.setRank(Rank.SPECIES);
    doc5.setVernacularNames(Arrays.asList("abcde", "fghij"));

    NameUsageDocument doc6 = new NameUsageDocument();
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

    NameUsageDocument doc1 = new NameUsageDocument();
    doc1.setDatasetKey(1);
    doc1.setUsageId("1");
    doc1.setRank(Rank.SPECIES);
    doc1.setScientificName("Larus fuscus");

    NameUsageDocument doc2 = new NameUsageDocument();
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

    // Let's go through the whose conversion process from NameUsageWrapper to NameUsageDocument

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

    System.out.println("score1: " + score1); // just curious
    System.out.println("score2: " + score2);
    System.out.println("score3: " + score3);
    System.out.println("score4: " + score4);
    System.out.println("score5: " + score5);
    System.out.println("score6: " + score6);

    // Since we now issue disjunction_max queries, there's just no predicting scores any longer.
    // assertTrue(score1 > score2);
    // assertTrue(score2 > score3);
    // assertTrue(score3 > score4);
    // assertTrue(score4 > score5);
    // assertTrue(score5 > score6);
  }

  private static boolean containsUsageIds(NameUsageSuggestResponse response, NameUsageDocument... docs) {
    Set<String> expected = Arrays.stream(docs).map(NameUsageDocument::getUsageId).collect(toSet());
    Set<String> actual = response.getSuggestions().stream().map(NameUsageSuggestion::getUsageId).collect(toSet());
    return expected.equals(actual);
  }

}
