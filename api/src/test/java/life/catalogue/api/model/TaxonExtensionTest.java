package life.catalogue.api.model;

import org.junit.Test;

public class TaxonExtensionTest {

  @Test
  public void classCompat() throws Exception {
    var tp = new TaxonProperty();
    var te = new TaxonExtension<TaxonProperty>();
    te.setObj(tp);
  }
}