package life.catalogue.common.tax;

import life.catalogue.api.model.Name;

import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NameFormatterTest {

  /**
   * https://github.com/Sp2000/colplus-backend/issues/478
   */
  @Test
  public void bacterialInfraspec() throws Exception {
    Name n = new Name();
    n.setGenus("Spirulina");
    n.setSpecificEpithet("subsalsa");
    n.setInfraspecificEpithet("subsalsa");
    n.setRank(Rank.INFRASPECIFIC_NAME);


    assertEquals("Spirulina subsalsa subsalsa", NameFormatter.scientificName(n));
    n.setCode(NomCode.BACTERIAL);
    assertEquals("Spirulina subsalsa subsalsa", NameFormatter.scientificName(n));
  }

  @Test
  public void canonical() throws Exception {
    Name n = new Name();
    n.setGenus("Spirulina");
    n.setSpecificEpithet("subsalsa");
    n.setInfraspecificEpithet("salsa");
    n.setRank(Rank.VARIETY);
    n.setScientificName("scientificName"); // to test if we get back a newly build name or the original string

    assertEquals("Spirulina subsalsa salsa", NameFormatter.canonicalName(n));

    // all the following should not matter for the canonical name
    n.setCode(NomCode.BACTERIAL);
    n.setAuthorship("Alberta");
    n.setCombinationAuthorship(Authorship.authors("Alberta"));
    n.setCandidatus(true);
    n.setNomStatus(NomStatus.UNACCEPTABLE);
    n.setNomenclaturalNote("nom note");
    n.setNotho(NamePart.GENERIC);
    assertEquals("Spirulina subsalsa salsa", NameFormatter.canonicalName(n));


    n.setInfraspecificEpithet(null);
    assertEquals("Spirulina subsalsa", NameFormatter.canonicalName(n));

    n.setSpecificEpithet(null);
    // not a valid genus name anymore
    assertEquals("scientificName", NameFormatter.canonicalName(n));

    n.setUninomial("Spirulina");
    assertEquals("Spirulina", NameFormatter.canonicalName(n));
  }

  @Test
  public void authors() throws Exception {
    Name n = new Name();
    n.setCombinationAuthorship(Authorship.yearAuthors("1967", "Morrison", "Hendrix"));
    n.setBasionymAuthorship(Authorship.yearAuthors("1923", "D.Reinhardt"));

    assertNull(n.getAuthorship());
    assertEquals("(D.Reinhardt, 1923) Morrison & Hendrix, 1967", NameFormatter.authorship(n));
  }

  @Test
  public void unparsed() throws Exception {
    // a phrase name
    Name n = new Name();
    n.setGenus("Acacia");
    n.setRank(Rank.SPECIES);
    n.setUnparsed("Bigge Island (A.A. Mitchell 3436) WA Herbarium");
    assertNull(n.getAuthorship());
    assertNull(NameFormatter.authorship(n));
    assertEquals("Acacia sp. Bigge Island (A.A. Mitchell 3436) WA Herbarium", NameFormatter.scientificName(n));
  }

}