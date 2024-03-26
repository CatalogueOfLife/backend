package life.catalogue.es.nu;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;

import life.catalogue.common.date.FuzzyDate;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.time.StopWatch;

import static org.junit.Assert.assertEquals;

/**
 * Focuses on the normalization methods within NameUsageWrapperConverter
 *
 */
public class NameUsageWrapperConverterTest {

  @Test
  public void roundtrip() throws IOException {
    // original input
    var nuw = TestEntityGenerator.newNameUsageTaxonWrapper();
    nuw.setGroup(TaxGroup.Algae);
    nuw.setPublisherKey(UUID.randomUUID());
    nuw.setSectorDatasetKey(4567);
    nuw.setDecisions(new ArrayList<>(List.of( // array list to avoid kryo not doing a deep copy
      new SimpleDecision(66, 456, EditorialDecision.Mode.UPDATE),
      new SimpleDecision(62, 456, EditorialDecision.Mode.BLOCK)
    )));
    nuw.setClassification(new ArrayList<>(List.of(
      SimpleName.sn("Karambula"),
      SimpleName.sn(Rank.ORDER, "Karambulales"),
      SimpleName.sn("d4f", Rank.PHYLUM, "Karambulatae", null) // authorship is not kept !!!
    )));
    var u = (Taxon) nuw.getUsage();
    u.setParentId("P68");
    u.setSectorKey(12);
    u.setScrutinizerID("456ZT");
    u.setExtinct(true);
    u.setScrutinizer("drftg");
    u.setScrutinizerDate(FuzzyDate.of("2008-08"));
    u.setEnvironments(Set.of(Environment.TERRESTRIAL, Environment.MARINE));
    u.setTemporalRangeStart("start");
    u.setTemporalRangeEnd("end");
    u.setTemporalRangeStart(GeoTime.byName("Neoarchean"));
    u.setTemporalRangeEnd(GeoTime.byName("Cretaceous"));
    u.setOrdinal(789);
    u.setNamePhrase("ftgzhj");
    u.setLink(URI.create("http://go.to/me"));
    u.setRemarks("drfthuj gtzhu jz7ghu");
    u.setIdentifier(List.of(
      Identifier.parse("col:4r56"),
      Identifier.parse("gbif:3456789")
    ));
    var n = u.getName();
    n.setSectorKey(13);
    n.setSectorMode(Sector.Mode.MERGE);
    n.setGender(Gender.FEMININE);
    n.setGenderAgreement(true);
    n.setNomStatus(NomStatus.ESTABLISHED);
    n.setPublishedInPage("14");
    n.setPublishedInYear(1988);
    n.setLink(URI.create("http://read.me"));
    n.setRemarks("NR guzgqszugwqtfdwqw");
    n.setEtymology("etym");
    n.setNomenclaturalNote("nom notes not good");

    var orig = TestEntityGenerator.copy(nuw); // we keep a copy as the converters modify the instance

    // convert into doc instance, which keeps a pruned payload for all non indexed data
    var doc = NameUsageWrapperConverter.toDocument(nuw);
    NameUsageWrapper nuw2 = NameUsageWrapperConverter.inflate(doc.getPayload());
    System.out.println("Payload length: " + doc.getPayload().length());
    System.out.println("Payload: " + doc.getPayload());
    NameUsageWrapperConverter.enrichPayload(nuw2, doc);

    assertEquals(nuw2, orig);
  }

  @Test
  public void testNormalizeWeakly1() {
    String s = NameUsageWrapperConverter.normalizeWeakly("Larus");
    assertEquals("larus", s);
  }

  @Test
  public void testNormalizeWeakly2() {
    String s = NameUsageWrapperConverter.normalizeWeakly("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalizeWeakly3() {
    String s = NameUsageWrapperConverter.normalizeWeakly("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalizeStrongly1a() {
    String s = NameUsageWrapperConverter.normalizeStrongly("Larus");
    System.out.println(s);
    assertEquals("lar", s);
  }

  @Test
  public void testNormalizeStrongly1b() {
    String s = NameUsageWrapperConverter.normalizeStrongly("Larus fuscus");
    assertEquals("larus fusc", s);
  }

  @Test
  public void testNormalizeStrongly1c() {
    String s = NameUsageWrapperConverter.normalizeStrongly("Larus fuscus fuscus");
    System.out.println(s);
    assertEquals("larus fuscus fusc", s);
  }

  @Test
  public void testNormalizeStrongly2() {
    String s = NameUsageWrapperConverter.normalizeStrongly("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalizeStrongly3() {
    String s = NameUsageWrapperConverter.normalizeStrongly("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalizeStrongly4() {
    String s = NameUsageWrapperConverter.normalizeStrongly("sylvestris");
    System.out.println(s);
    assertEquals("silvestr", s);
  }

}
