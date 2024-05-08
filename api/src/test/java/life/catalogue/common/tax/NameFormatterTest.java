package life.catalogue.common.tax;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.*;

import java.util.regex.Matcher;

import org.junit.Test;

import static org.junit.Assert.*;

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

    // bacterial authors dont use a comma before the year
    n.setCombinationAuthorship(ExAuthorship.yearAuthors("1999", "Döring", "Banki"));
    assertEquals("Döring et al. 1999", NameFormatter.authorship(n));
    n.setCode(NomCode.BOTANICAL);
    assertEquals("Döring & Banki, 1999", NameFormatter.authorship(n));
    n.setCode(null);
    assertEquals("Döring & Banki, 1999", NameFormatter.authorship(n));
  }

  @Test
  public void cultivars() throws Exception {
    Name n = new Name();
    n.setGenus("Brassica");
    n.setSpecificEpithet("oleracea");
    n.setCultivarEpithet("Golden Wonder");
    n.setRank(Rank.CULTIVAR);

    assertEquals("Brassica oleracea 'Golden Wonder'", NameFormatter.scientificName(n));
    n.setCode(NomCode.CULTIVARS);
    assertEquals("Brassica oleracea 'Golden Wonder'", NameFormatter.scientificName(n));

    n.setRank(Rank.CULTIVAR_GROUP);
    n.setCultivarEpithet("Capitata");
    assertEquals("Brassica oleracea Capitata Group", NameFormatter.scientificName(n));
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
    n.setCombinationAuthorship(ExAuthorship.authors("Alberta"));
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
    n.setCombinationAuthorship(ExAuthorship.yearAuthors("1967", "Morrison", "Hendrix"));
    n.setBasionymAuthorship(ExAuthorship.yearAuthors("1923", "D.Reinhardt"));

    assertNull(n.getAuthorship());
    assertEquals("(D.Reinhardt, 1923) Morrison & Hendrix, 1967", NameFormatter.authorship(n));
  }


  @Test
  public void linneanPattern() throws Exception {
    Matcher m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies (Pina)");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies (Pina) alba");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba Mill.");
    assertFalse(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies DC");
    assertFalse(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies 4-color");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba alpina");
    assertTrue(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba subsp. alpina");
    assertFalse(m.find());

    m = NameFormatter.LINNEAN_NAME_NO_AUTHOR.matcher("Abies alba ssp.");
    assertFalse(m.find());
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