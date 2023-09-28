package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NameType;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class IgnoreReasonTest {

  @Test
  public void reasonByNameType() {
    for (NameType nt : NameType.values()) {
      assertNotNull(nt.toString(), IgnoreReason.reasonByNameType(nt));
    }
  }
}