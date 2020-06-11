package life.catalogue.common.tax;

import life.catalogue.api.model.Name;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

}