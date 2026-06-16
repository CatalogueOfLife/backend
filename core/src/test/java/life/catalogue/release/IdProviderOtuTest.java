package life.catalogue.release;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameWithNidx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IdProviderOtuTest {

  private static SimpleNameWithNidx name(String name) {
    var sn = new SimpleNameWithNidx();
    sn.setName(name);
    return sn;
  }
  static String otuId(SimpleName u) {
    return IdProvider.otuId(u, null);
  }

  @Test
  public void otuId() {
    // UNITE species hypothesis codes - used verbatim, case insensitive match but original casing kept
    assertEquals("SH19186714.17FU", otuId(name("SH19186714.17FU")));
    assertEquals("sh19186714.17fu", otuId(name("sh19186714.17fu")));
    assertEquals("SH1.10FU", otuId(name("SH1.10FU")));

    // BOLD codes - colon replaced by a dot
    assertEquals("BOLD.AAA3374", otuId(name("BOLD:AAA3374")));
    assertEquals("BOLD.AAA3374", otuId(name("bold:AAA3374")));

    // not in scope
    assertNull(otuId(name(null)));
    assertNull(otuId(name("Abies alba")));
    assertNull(otuId(name("Abies alba Mill.")));
    assertNull(otuId(name("0-14-0-10-38-17 sp002774085"))); // GTDB OTU, out of scope
    assertNull(otuId(name("2B4K"))); // a LATIN29 encoded int id
    assertNull(otuId(name("SH19186714.17"))); // missing FU suffix
    assertNull(otuId(name("BOLD:AAA 3374"))); // space not allowed
  }
}
