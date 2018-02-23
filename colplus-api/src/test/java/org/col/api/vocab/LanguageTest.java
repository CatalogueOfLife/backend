/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.api.vocab;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LanguageTest {

  @Test
  public void testFromIsoCode() throws Exception {
    assertEquals(Language.ENGLISH, Language.fromIsoCode("en").get());
    assertEquals(Language.ESTONIAN, Language.fromIsoCode("et").get());
    assertEquals(Language.ZULU, Language.fromIsoCode("ZU").get());
    assertEquals(Language.GERMAN, Language.fromIsoCode("de").get());
    assertEquals(Language.GERMAN, Language.fromIsoCode("DEU").get());
    assertEquals(Language.GERMAN, Language.fromIsoCode("Deu").get());
  }

  @Test
  public void testGetIso2LetterCode() throws Exception {
    for (Language l : Language.values()) {
      assertNotNull(l.getIso2LetterCode());
      // make sure its lower case
      assertEquals(l.getIso2LetterCode().toLowerCase(), l.getIso2LetterCode());
    }
    assertEquals("en", Language.ENGLISH.getIso2LetterCode());
    assertEquals("de", Language.GERMAN.getIso2LetterCode());
  }

  @Test
  public void testGetIso3LetterCode() throws Exception {
    for (Language l : Language.values()) {
      assertNotNull(l.getIso3LetterCode());
      // make sure its lower case
      assertEquals(l.getIso3LetterCode().toLowerCase(), l.getIso3LetterCode());
    }
    assertEquals("eng", Language.ENGLISH.getIso3LetterCode());
    assertEquals("deu", Language.GERMAN.getIso3LetterCode());
  }

  @Test
  public void testGetLocale() throws Exception {
    for (Language l : Language.values()) {
      assertNotNull(l);
      assertNotNull(l.getLocale());
    }
    assertEquals(Locale.ENGLISH, Language.ENGLISH.getLocale());
    assertEquals(Locale.GERMAN, Language.GERMAN.getLocale());
  }

  @Test
  public void testGetTitleEnglish() throws Exception {
    for (Language l : Language.values()) {
      assertNotNull(l.getTitleEnglish());
    }
    assertEquals("English", Language.ENGLISH.getTitleEnglish());
    assertEquals("German", Language.GERMAN.getTitleEnglish());
  }

  @Test
  public void testGetTitleNative() throws Exception {
    for (Language l : Language.values()) {
      assertNotNull(l.getTitleNative());
    }
    assertEquals("English", Language.ENGLISH.getTitleNative());
    assertEquals("Deutsch", Language.GERMAN.getTitleNative());
  }

}
