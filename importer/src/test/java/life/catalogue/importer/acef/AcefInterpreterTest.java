package life.catalogue.importer.acef;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InterpreterTestAbstractBase;
import life.catalogue.importer.neo.NeoDb;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AcefInterpreterTest extends InterpreterTestAbstractBase<AcefInterpreter> {

  @Test
  public void interpretSynonym() {
    VerbatimRecord v = new VerbatimRecord();
    v.put(AcefTerm.ID, "27105");
    v.put(AcefTerm.Genus, "Cerynia");
    v.put(AcefTerm.GSDNameStatus, "new combination, valid: No");
    v.put(AcefTerm.SpeciesEpithet, "albata");
    v.put(AcefTerm.AcceptedTaxonID, "13067");
    v.put(AcefTerm.Sp2000NameStatus, "Unambiguous Synonym");
    v.put(AcefTerm.InfraSpeciesEpithet, "var. triscipta");
    v.put(AcefTerm.InfraSpeciesAuthorString, "(Walker, 1858)");

    var nu = interpreter.interpretSynonym(v).get();
    var n = nu.usage.getName();
    assertEquals("Cerynia albata var. triscipta", n.getScientificName());
    assertEquals("(Walker, 1858)", n.getAuthorship());
    assertEquals(Rank.VARIETY, n.getRank());
    assertNull(n.getUninomial());
    assertEquals("Cerynia", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("albata", n.getSpecificEpithet());
    assertEquals("triscipta", n.getInfraspecificEpithet());
    assertEquals(new Authorship(), n.getCombinationAuthorship());
    assertEquals("1858", n.getBasionymAuthorship().getYear());
    assertEquals("Walker", n.getBasionymAuthorship().getAuthors().get(0));
    assertEquals(1, n.getBasionymAuthorship().getAuthors().size());
    assertEquals("new combination, valid: No", n.getRemarks());

    // same but without rank marker in epithet
    v.put(AcefTerm.InfraSpeciesEpithet, "triscipta");
    nu = interpreter.interpretSynonym(v).get();
    n = nu.usage.getName();
    assertEquals("Cerynia albata triscipta", n.getScientificName());
    assertEquals("(Walker, 1858)", n.getAuthorship());
    assertEquals(Rank.SUBSPECIES, n.getRank());
    assertNull(n.getUninomial());
    assertEquals("Cerynia", n.getGenus());
    assertNull(n.getInfragenericEpithet());
    assertEquals("albata", n.getSpecificEpithet());
    assertEquals("triscipta", n.getInfraspecificEpithet());
    assertEquals(new Authorship(), n.getCombinationAuthorship());
    assertEquals("1858", n.getBasionymAuthorship().getYear());
    assertEquals("Walker", n.getBasionymAuthorship().getAuthors().get(0));
    assertEquals(1, n.getBasionymAuthorship().getAuthors().size());
    assertEquals("new combination, valid: No", n.getRemarks());
  }

  @Test
  public void sensuAuthorship() {
    // https://github.com/CatalogueOfLife/data/issues/902
    VerbatimRecord v = new VerbatimRecord();
    v.put(AcefTerm.AcceptedTaxonID, "1");
    v.put(AcefTerm.Genus, "Pseudofolliculina");
    v.put(AcefTerm.SpeciesEpithet, "grandis");
    v.put(AcefTerm.Sp2000NameStatus, "accepted name");

    v.put(AcefTerm.AuthorString, "Miller sensu Busch, 1930");
    var nu = interpreter.interpretSynonym(v).get();
    assertEquals("Pseudofolliculina grandis", nu.usage.getName().getScientificName());
    assertEquals("Miller", nu.usage.getName().getAuthorship());
    assertNull(nu.usage.getNamePhrase());
    assertEquals("Busch, 1930", nu.usage.getAccordingTo());

    v.put(AcefTerm.AuthorString, "(sensu Mereschkowsky, 1878) Jankowski, 1992");
    nu = interpreter.interpretSynonym(v).get();
    assertEquals("Pseudofolliculina grandis", nu.usage.getName().getScientificName());
    assertNull(nu.usage.getName().getAuthorship());
    assertNull(nu.usage.getAccordingTo());
    assertEquals("(sensu Mereschkowsky, 1878) Jankowski, 1992", nu.usage.getNamePhrase());

    v.put(AcefTerm.AuthorString, "(Mereschkowsky, 1878) sensu Jankowski, 1992");
    nu = interpreter.interpretSynonym(v).get();
    assertEquals("Pseudofolliculina grandis", nu.usage.getName().getScientificName());
    assertEquals("(Mereschkowsky, 1878)", nu.usage.getName().getAuthorship());
    assertEquals("Jankowski, 1992", nu.usage.getAccordingTo());
    assertNull(nu.usage.getNamePhrase());
  }

  @Override
  protected AcefInterpreter buildInterpreter(DatasetSettings settings, ReferenceFactory refFactory, NeoDb store) {
    var mappings = new MappingInfos();
    return new AcefInterpreter(settings, mappings, refFactory, store);
  }
}