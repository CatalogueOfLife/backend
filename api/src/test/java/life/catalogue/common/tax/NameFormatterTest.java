package life.catalogue.common.tax;

import life.catalogue.api.model.Name;
import org.gbif.nameparser.api.Authorship;
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