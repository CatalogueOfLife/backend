package life.catalogue.matching.authorship.corpus;

import life.catalogue.api.vocab.TaxGroup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AuthorPairTest {

  /** the group both names of a pair belong to: the broader of two nested ones, none for disparate ones */
  @Test
  public void commonGroup() {
    assertEquals(TaxGroup.Plants, AuthorPair.commonGroup(TaxGroup.Plants, TaxGroup.Angiosperms));
    assertEquals(TaxGroup.Plants, AuthorPair.commonGroup(TaxGroup.Angiosperms, TaxGroup.Plants));
    assertEquals(TaxGroup.Molluscs, AuthorPair.commonGroup(null, TaxGroup.Molluscs));
    assertNull(AuthorPair.commonGroup(TaxGroup.Molluscs, TaxGroup.Angiosperms));
    assertNull(AuthorPair.commonGroup(null, null));
  }
}
