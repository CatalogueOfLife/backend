package life.catalogue.api.model;

import life.catalogue.api.vocab.Country;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OrganisationTest {

  @Test
  public void getLabel() {
    Organisation o = new Organisation();
    assertNull(o.getLabel());

    o.setName("BGBM");
    assertEquals("BGBM", o.getLabel());

    o.setCountry(Country.GERMANY);
    assertEquals("BGBM, Germany", o.getLabel());

    o.setDepartment("Abteilung für Bioinformatik");
    assertEquals("Abteilung für Bioinformatik, BGBM, Germany", o.getLabel());

    o.setCity("Berlin");
    o.setState("Berlin-Brandenburg");
    assertEquals("Abteilung für Bioinformatik, BGBM, Berlin, Berlin-Brandenburg, Germany", o.getLabel());
  }
}