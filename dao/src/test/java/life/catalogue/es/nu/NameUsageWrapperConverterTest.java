package life.catalogue.es.nu;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.search.NameUsageWrapper;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Focuses on the normalization methods within NameUsageWrapperConverter
 *
 */
public class NameUsageWrapperConverterTest {

  void roundtrip(NameUsageWrapper nuw) throws IOException {
    var base64 = NameUsageWrapperConverter.encode(nuw);
    NameUsageWrapper nuw2 = NameUsageWrapperConverter.decode(base64);
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
    NameUsageWrapper nuw2 = NameUsageWrapperConverter.decode(doc.getPayload());
    System.out.println("Payload length: " + doc.getPayload().length());
    System.out.println("Payload: " + doc.getPayload());
    NameUsageWrapperConverter.enrichPayload(nuw2, doc);

    assertEquals(nuw2, orig);
  }

  @Test
  public void testNormalize1() {
    String s = NameUsageWrapperConverter.normalize("Larus");
    assertEquals("larus", s);
  }

  @Test
  public void testNormalize2() {
    String s = NameUsageWrapperConverter.normalize("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalize3() {
    String s = NameUsageWrapperConverter.normalize("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalize1A() {
    String s = NameUsageWrapperConverter.normalize("Larus");
    System.out.println(s);
    assertEquals("lar", s);
  }

  @Test
  public void testNormalize1B() {
    String s = NameUsageWrapperConverter.normalize("Larus fuscus");
    assertEquals("larus fusc", s);
  }

  @Test
  public void testNormalize1C() {
    String s = NameUsageWrapperConverter.normalize("Larus fuscus fuscus");
    System.out.println(s);
    assertEquals("larus fuscus fusc", s);
  }

  @Test
  public void testNormalize2b() {
    String s = NameUsageWrapperConverter.normalize("等待");
    assertEquals("等待", s);
  }

  @Test
  public void testNormalize3b() {
    String s = NameUsageWrapperConverter.normalize("sérieux");
    assertEquals("serieux", s);
  }

  @Test
  public void testNormalize4() {
    String s = NameUsageWrapperConverter.normalize("sylvestris");
    System.out.println(s);
    assertEquals("silvestr", s);
  }

}
