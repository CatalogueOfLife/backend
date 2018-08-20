package org.col.csl;

import org.col.api.TestEntityGenerator;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class CslUtilTest {

  @Test
  public void makeBibliography() {
    for (int i = 0; i < 100; i++) {
      System.out.println(CslUtil.buildCitation(TestEntityGenerator.newReference()));
    }
  }
}
