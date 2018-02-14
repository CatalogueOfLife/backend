package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class AcefTermTest {

  @Test
  public void names() throws Exception {
    assertEquals("AcceptedTaxonID", AcefTerm.AcceptedTaxonID.simpleName());
    assertEquals("http://rs.col.plus/terms/acef/AcceptedTaxonID", AcefTerm.AcceptedTaxonID.qualifiedName());
  }

}