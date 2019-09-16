package org.col.api.vocab;

import org.junit.Test;

import static org.junit.Assert.*;

public class LanguageTest {
  
  @Test
  public void testBuild() {
    assertTrue(Language.LANGUAGES.size() > 7800);
    for (Language l : Language.LANGUAGES.values()) {
      assertNotNull(l.getCode());
      assertNotNull(l.getTitle());
      assertEquals(l.getCode(), 3,l.getCode().length());
      assertEquals(l.getCode(), l.getCode(), l.getCode().toLowerCase());
    }
    // check first and last code exists
    assertNotNull(Language.byCode("aaa"));
    assertNotNull(Language.byCode("zzj"));
  }
}