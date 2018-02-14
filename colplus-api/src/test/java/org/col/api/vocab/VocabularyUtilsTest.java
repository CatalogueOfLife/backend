package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VocabularyUtilsTest {

  @Test
  public void factory() throws Exception {
    assertEquals(AcefTerm.Source, VocabularyUtils.TF.findTerm("acef:source"));
  }
}