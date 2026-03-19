package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class FormattableNameTest {

  @Test
  public void getAlphaIndex() {
    LinneanNameUsage fn = new LinneanNameUsage();
    fn.setScientificName("Abies alba");
    assertEquals("A", fn.getAlphaIndex());

    fn.setScientificName("Aries");
    assertEquals("A", fn.getAlphaIndex());
  }
}