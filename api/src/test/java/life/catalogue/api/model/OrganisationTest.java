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
  }
}