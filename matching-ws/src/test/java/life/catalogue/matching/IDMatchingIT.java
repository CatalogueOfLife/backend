package life.catalogue.matching;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.model.*;
import life.catalogue.matching.service.IndexingService;
import life.catalogue.matching.service.MatchingService;
import life.catalogue.matching.util.Dictionaries;

import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.gbif.nameparser.api.Rank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ID matching
 */
public class IDMatchingIT {

  private static MatchingService matcher;

  @BeforeAll
  public static void buildMatcher() throws IOException {

    // create the main datasetIndex
    final DatasetIndex datasetIndex = MatchingTestConfiguration.provideIndex();
    matcher =
      new MatchingService(datasetIndex, MatchingTestConfiguration.provideSynonyms(), Dictionaries.createDefault());

    // create the join index
    List<NameUsage> usages = List.of(
      new NameUsage("ext-1", null, "Animalia", null, TaxonomicStatus.ACCEPTED.toString(), Rank.KINGDOM.toString(),  null, null, null),
      new NameUsage("ext-2", "ext-1", "Arthropoda", null, TaxonomicStatus.ACCEPTED.toString(), Rank.PHYLUM.toString(),  null, null, null),
      new NameUsage("ext-3", "ext-2", "Callipodida",  null, TaxonomicStatus.ACCEPTED.toString(), Rank.ORDER.toString(),  null, null, null),
      new NameUsage("ext-4", "ext-3", "Abacionidae",  null, TaxonomicStatus.ACCEPTED.toString(), Rank.FAMILY.toString(),  null, null, null),
      new NameUsage("ext-5", "ext-4", "Abacion", null, TaxonomicStatus.ACCEPTED.toString(), Rank.GENUS.toString(),  null, null, null),
      new NameUsage("ext-6", "ext-5", "Abacion tesselatum",  null, TaxonomicStatus.ACCEPTED.toString(), Rank.SPECIES.toString(),  null, null, null),
      new NameUsage("ext-7", "ext-6", "Abacion tesselatum iamnew",  null, TaxonomicStatus.ACCEPTED.toString(), Rank.SPECIES.toString(),  null, null, null)
    );

    Directory tempJoinIndex = IndexingService.newMemoryIndex(usages);
    Directory joinIndex = new ByteBuffersDirectory();

    Long[] counts = IndexingService.createJoinIndex(
      matcher,
      tempJoinIndex,
      joinIndex,
      false,
      false,
      1
    );

    Dataset dataset = new Dataset();
    dataset.setKey(1);
    dataset.setAlias("DUMMY_IDS");
    dataset.setTitle("Dummy dataset for testing");
    dataset.setPrefix("ext-");
    dataset.setPrefixMapping(List.of("other-ext-", "other-ext2-"));
    dataset.setTaxonCount(counts[0]);
    dataset.setMatchesToMainIndex(counts[1]);
    datasetIndex.initWithIdentifierDir(dataset, joinIndex);
  }

  // Tests
  @Test
  public void testJoinHigherTaxa(){

    NameUsageMatch match = matcher.match(new NameUsageQuery(
      null, "ext-4", null, null, null, null,
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match);
    assertNotNull(match.getUsage());
    assertEquals("7228", match.getUsage().getKey());
    assertEquals("Abacionidae", match.getUsage().getName());
  }

  @Test
  public void testJoinLeafTaxa(){

    NameUsageMatch match1 = matcher.match(new NameUsageQuery(
      null, "ext-6", null, null, null, null,
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match1);
    assertNotNull(match1.getUsage());
    assertEquals("1011638", match1.getUsage().getKey());

    NameUsageMatch match2 = matcher.match(new NameUsageQuery(
      null, null, null, null, "Abacion tesselatum", null,
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match2);
    assertNotNull(match2.getUsage());
    assertEquals("1011638", match2.getUsage().getKey());
  }

  @Test
  public void testJoinLeafTaxaWithPrefix(){

    NameUsageMatch match1 = matcher.match(new NameUsageQuery(
      null, "other-ext-", null, null, "Abacion tesselatum", null,
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match1);
    assertNotNull(match1.getUsage());
    assertEquals("1011638", match1.getUsage().getKey());

    NameUsageMatch match2 = matcher.match(new NameUsageQuery(
      null, "other-ext2-", null, null, "Abacion tesselatum", null,
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match2);
    assertNotNull(match2.getUsage());
    assertEquals("1011638", match2.getUsage().getKey());
  }

  @Test
  public void testIDandNameInconsistent(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, "ext-6", null, null, "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertNotNull(match3.getUsage());
    assertEquals("1011638", match3.getUsage().getKey());
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.SCIENTIFIC_NAME_AND_ID_INCONSISTENT));
  }

  @Test
  public void testTaxonIDNotFound(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, "ext-123", null, null, "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.TAXON_ID_NOT_FOUND));
  }

  @Test
  public void testTaxonConceptIDNotFound(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, null, "ext-123", null, "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.TAXON_CONCEPT_ID_NOT_FOUND));
  }

  @Test
  public void testScientificNameIDNotFound(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, null, null, "ext-123", "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.SCIENTIFIC_NAME_ID_NOT_FOUND));
  }


  @Test
  public void testTaxonIDIgnored(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, "ext-7", null, null, "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.TAXON_ID_NOT_FOUND));
  }

  @Test
  public void testTaxonConceptIDIgnored(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, null, "ext-7", null, "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.TAXON_CONCEPT_ID_NOT_FOUND));
  }

  @Test
  public void testScientificNameIDIgnored(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, null, null, "ext-7", "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.SCIENTIFIC_NAME_ID_NOT_FOUND));
  }

  @Test
  public void testTaxonNameAndIDAmbiguous(){

    NameUsageMatch match3 = matcher.match(new NameUsageQuery(
      null, null, null, "ext-6", "Abacion nonsense", "Abacionidae",
      null,null,null, null, null, null,
      false, false
    ));

    assertNotNull(match3);
    assertTrue(match3.getDiagnostics().getIssues().contains(Issue.TAXON_MATCH_NAME_AND_ID_AMBIGUOUS));
  }
}
