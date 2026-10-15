package life.catalogue.dao;

import life.catalogue.api.model.TypeDesignation;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.api.vocab.TypeStatus;
import life.catalogue.api.vocab.area.Country;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TaxonDaoTest {

  @Test
  public void typeContent() {
    var tm = new TypeMaterial();
    var d = new TypeDesignation("n1", null, tm);
    String x = TaxonDao.typeContent(d);
    assertEquals("", x);

    tm.setCollector("COL");
    x = TaxonDao.typeContent(d);
    assertEquals("col|col", x);

    tm.setCountry(Country.GERMANY);
    x = TaxonDao.typeContent(d);
    assertEquals("col|germany|col", x);

    // the status is the designations, not the specimens
    d.setStatus(TypeStatus.HOLOTYPE);
    x = TaxonDao.typeContent(d);
    assertEquals("col|holotype|germany|col", x);
    // the name is not part of it
    assertEquals(x, TaxonDao.typeContent(new TypeDesignation("n2", TypeStatus.HOLOTYPE, tm)));
  }

  @Test
  public void removeBrokenTags() {
    assertNull(TaxonDao.removeBrokenTags(null));
    assertEquals("", TaxonDao.removeBrokenTags(""));
    assertEquals(" ", TaxonDao.removeBrokenTags(" "));
    assertEquals("tm", TaxonDao.removeBrokenTags("tm"));
    assertEquals("<i>Annals of the South African Museum</i>", TaxonDao.removeBrokenTags("<i>Annals of the South African Museum</i>"));
    assertEquals("<b>Annals of the South African Museum</i>", TaxonDao.removeBrokenTags("<b>Annals of the South African Museum</i>"));
    assertEquals("<b>Annals of the South African Museum</b>", TaxonDao.removeBrokenTags("<b>Annals of the South African Museum</b>"));
    assertEquals("b>Annals of the South African Museum</i", TaxonDao.removeBrokenTags("b>Annals of the South African Museum</i"));
    // removed
    assertEquals("Annals of the South African Museum", TaxonDao.removeBrokenTags("i>Annals of the South African Museum</i"));
    assertEquals("Annals of the South African Museum", TaxonDao.removeBrokenTags("b>Annals of the South African Museum</b"));
    assertEquals("Barnard, K. H. (1974). Contributions to the knowledge of South African Marine Mollusca. Part VII. Revised fauna list. Annals of the South African Museum, 47(5): 663–781. https://www.molluscabase.org/aphia.php?p=sourcedetails&id=172653", TaxonDao.removeBrokenTags("Barnard, K. H. (1974). Contributions to the knowledge of South African Marine Mollusca. Part VII. Revised fauna list. i>Annals of the South African Museum</i, 47(5): 663–781. https://www.molluscabase.org/aphia.php?p=sourcedetails&id=172653"));
  }



}
