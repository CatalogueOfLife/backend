package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;

import org.junit.Test;

import static org.junit.Assert.*;

public class TaxonDaoTest {

  @Test
  public void typeContent() {
    var tm = new TypeMaterial();
    String x = TaxonDao.typeContent(tm);
    assertEquals("", x);

    tm.setCollector("COL");
    x = TaxonDao.typeContent(tm);
    assertEquals("col|col", x);

    tm.setCountry(Country.GERMANY);
    x = TaxonDao.typeContent(tm);
    assertEquals("col|germany|col", x);
  }

}
