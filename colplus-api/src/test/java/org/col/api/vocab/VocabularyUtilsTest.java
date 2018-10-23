package org.col.api.vocab;

import org.col.api.util.VocabularyUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VocabularyUtilsTest {

  @Test
  public void factory() throws Exception {
    assertEquals(AcefTerm.Details, VocabularyUtils.TF.findTerm("acef:source"));
    assertEquals(DwcTerm.family, VocabularyUtils.TF.findTerm("dwc:family"));
    assertEquals(DwcTerm.family, VocabularyUtils.TF.findTerm("family"));
    assertEquals(AcefTerm.Family, VocabularyUtils.TF.findTerm("acef:family"));
  }
}