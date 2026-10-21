package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class TaxonPropertyTest {

  static TaxonProperty prop(String property, String value) {
    var tp = new TaxonProperty();
    tp.setProperty(property);
    tp.setValue(value);
    return tp;
  }

  @Test
  public void isDuplicate() {
    assertTrue(prop("size", "12mm").isDuplicate(prop("size", "12mm")));
    // same key, different value
    assertTrue(prop("size", "12mm").isDuplicate(prop("size", "3cm")));
    assertFalse(prop("size", "12mm").isDuplicate(prop("colour", "12mm")));
  }
}
