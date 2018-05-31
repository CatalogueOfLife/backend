package org.col.csl;

import org.col.api.model.CslDate;
import org.col.api.vocab.CSLRefType;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CslDataConverterTest {

  @Test
  @Ignore
  public void toCSLItemData() {
  }

  @Test
  public void toCSLType() {
    for (CSLRefType t: CSLRefType.values()) {
      assertNotNull(CslDataConverter.toCSLType(t));
    }
  }

  @Test
  public void toCSLDate() {
    assertNull(CslDataConverter.toCSLDate(null));
    CslDate d = new CslDate();
    assertNotNull(CslDataConverter.toCSLDate(d));
    d.setCirca(true);
    assertNotNull(CslDataConverter.toCSLDate(d));
    d.setSeason("spring");
    d.setRaw("my spring");
    assertNotNull(CslDataConverter.toCSLDate(d));
  }

}