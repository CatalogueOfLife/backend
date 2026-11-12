package life.catalogue.metadata.eml;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CitationFormatter;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.area.Country;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

import org.gbif.api.model.registry.Contact;
import org.gbif.metadata.eml.parse.DatasetEmlParser;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Datasets ChecklistBank registers in GBIF publish their metadata through an EML endpoint serving this EML.
 * The GBIF registry reads it with its own parser, so make sure that parser gets the essentials right.
 */
public class EmlWriterGbifTest {

  @BeforeClass
  public static void registerFormatter() {
    CitationFormatter.register(new life.catalogue.common.csl.CslCitationFormatter());
  }

  @Test
  public void gbifParser() throws Exception {
    Dataset d = new Dataset();
    d.setKey(1234);
    d.setTitle("Checklist of the ants of Brandenburg");
    d.setAlias("BB ants");
    d.setDescription("All ants known from the German state of Brandenburg.");
    d.setDoi(DOI.col("ds1234"));
    d.setLicense(License.CC_BY);
    d.setUrl(URI.create("https://ants.example.org"));
    d.setKeyword(List.of("ants", "Formicidae"));
    d.setCreator(List.of(
      new Agent(null, "Ulla", "Uhlig", null, null, null, "Potsdam", null, Country.GERMANY, "ulla@example.org", null, null)
    ));
    d.setContact(new Agent(null, "Kai", "Kontakt", null, null, null, "Wien", null, Country.AUSTRIA, "kai@example.org", null, null));
    d.setPublisher(Agent.organisation("Museum für Naturkunde"));
    d.setSource(List.of(Citation.create("Seifert B. 2018. The ants of Central and North Europe.")));

    var out = new ByteArrayOutputStream();
    EmlWriter.write(d, out);
    var g = DatasetEmlParser.build(out.toByteArray());

    assertEquals("Checklist of the ants of Brandenburg", g.getTitle());
    assertTrue(g.getDescription().contains("Brandenburg"));
    assertEquals(org.gbif.api.vocabulary.License.CC_BY_4_0, g.getLicense());
    assertEquals(new org.gbif.api.model.common.DOI("10.48580/ds1234"), g.getDoi());
    assertEquals(URI.create("https://ants.example.org"), g.getHomepage());

    Contact creator = contact(g.getContacts(), "Uhlig");
    assertEquals(org.gbif.api.vocabulary.Country.GERMANY, creator.getCountry());
    assertEquals(List.of("ulla@example.org"), creator.getEmail());
    Contact contact = contact(g.getContacts(), "Kontakt");
    assertEquals(org.gbif.api.vocabulary.Country.AUSTRIA, contact.getCountry());
  }

  /**
   * A dataset synced from GBIF lists its GBIF DOI as an identifier. GBIF makes the ChecklistBank DOI the DOI of the
   * dataset and keeps the GBIF DOI as an alternate identifier.
   */
  @Test
  public void chosenDoi() throws Exception {
    Dataset d = new Dataset();
    d.setKey(1234);
    d.setTitle("Checklist of the ants of Brandenburg");
    d.setLicense(License.CC_BY);
    d.setDoi(DOI.col("ds1234"));
    d.addIdentifier(new Identifier(new DOI("10.15468/abcd")));

    var out = new ByteArrayOutputStream();
    EmlWriter.write(d, out);
    var g = DatasetEmlParser.build(out.toByteArray());

    assertEquals(new org.gbif.api.model.common.DOI("10.48580/ds1234"), g.getDoi());
    assertTrue(g.getIdentifiers().stream().anyMatch(i -> i.getIdentifier().contains("10.15468/abcd")));
  }

  private static Contact contact(List<Contact> contacts, String lastName) {
    return contacts.stream()
      .filter(c -> lastName.equals(c.getLastName()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("No contact " + lastName + " in " + contacts));
  }
}
