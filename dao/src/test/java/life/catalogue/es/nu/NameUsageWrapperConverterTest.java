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

  void roundtrip(NameUsageWrapper nuw) throws IOException {
    var base64 = NameUsageWrapperConverter.deflate(nuw);
    NameUsageWrapper nuw2 = NameUsageWrapperConverter.inflate(base64);
    System.out.println("Payload length: " + base64.length());
    assertEquals(nuw2, nuw);
  }

  @Test
  public void roundtrip() throws IOException {
    // full Taxon object
    roundtrip(TestEntityGenerator.newNameUsageTaxonWrapperComplete());
  }
  @Test
  public void testSynonym() throws IOException {
    roundtrip(TestEntityGenerator.newNameUsageSynonymWrapper());
  }

  @Test
  public void testBareName() throws IOException {
    roundtrip(TestEntityGenerator.newNameUsageBareNameWrapper());
  }
  @Test
  public void roundtripPayload() throws IOException {
    // original input
    var nuw = TestEntityGenerator.newNameUsageTaxonWrapperComplete();
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
