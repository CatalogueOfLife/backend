/*
 * Copyright 2014 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.api.vocab;

import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class CountryTest {
  
  @Test
  public void testFromIsoCode() throws Exception {
    Assert.assertEquals(Country.ARGENTINA, Country.fromIsoCode("ar").get());
    assertEquals(Country.ARGENTINA, Country.fromIsoCode("AR").get());
  }
  
  @Test
  public void testGetIso2LetterCode() throws Exception {
    Set<String> codes = Sets.newHashSet();
    for (Country l : Country.values()) {
      assertNotNull(l.getIso2LetterCode());
      // make sure its upper case
      assertEquals(l.getIso2LetterCode().toUpperCase(), l.getIso2LetterCode());
      // make sure its unique
      assertFalse(codes.contains(l.getIso2LetterCode()));
      codes.add(l.getIso2LetterCode());
    }
    assertEquals("DE", Country.GERMANY.getIso2LetterCode());
    assertEquals("GB", Country.UNITED_KINGDOM.getIso2LetterCode());
  }
  
  @Test
  public void testGetIso3LetterCode() throws Exception {
    Set<String> codes = Sets.newHashSet();
    for (Country l : Country.values()) {
      assertNotNull(l.getIso3LetterCode());
      // make sure its upper case
      assertEquals(l.getIso3LetterCode().toUpperCase(), l.getIso3LetterCode());
      // make sure its unique
      assertFalse(codes.contains(l.getIso3LetterCode()));
      codes.add(l.getIso3LetterCode());
    }
    assertEquals("GBR", Country.UNITED_KINGDOM.getIso3LetterCode());
    assertEquals("DEU", Country.GERMANY.getIso3LetterCode());
  }
  
  @Test
  public void testGetIsoNumericalCode() throws Exception {
    Set<Integer> codes = Sets.newHashSet();
    for (Country l : Country.values()) {
      assertNotNull(l.getIsoNumericalCode());
      // make sure its unique
      assertFalse(codes.contains(l.getIsoNumericalCode()));
      codes.add(l.getIsoNumericalCode());
    }
    assertEquals("GBR", Country.UNITED_KINGDOM.getIso3LetterCode());
    assertEquals("DEU", Country.GERMANY.getIso3LetterCode());
  }
  
  @Test
  public void testIsCustomCode() throws Exception {
    for (Country l : Country.values()) {
      if (l.isOfficial()) {
        assertFalse(Country.isCustomCode(l.getIso2LetterCode()));
        assertFalse(Country.isCustomCode(l.getIso3LetterCode()));
      } else {
        assertTrue(Country.isCustomCode(l.getIso2LetterCode()));
        assertTrue(Country.isCustomCode(l.getIso3LetterCode()));
      }
    }
  }
  
  @Test
  public void testIsOfficial() throws Exception {
    for (Country l : Country.OFFICIAL_COUNTRIES) {
      assertTrue(l.isOfficial());
    }
    int officialCountries = Country.OFFICIAL_COUNTRIES.size();
    int allCountries = Country.values().length;
    assertTrue(allCountries > officialCountries);
    assertEquals(249, officialCountries);
  }
  
  @Test
  public void testgetTitle() throws Exception {
    for (Country l : Country.values()) {
      assertNotNull(l.getTitle());
      assertTrue(l.getTitle().length() > 2);
    }
    assertEquals("United Kingdom", Country.UNITED_KINGDOM.getTitle());
    assertEquals("Germany", Country.GERMANY.getTitle());
  }
  
  @Test
  public void testCodeUniqueness() {
    Set<String> codes = Sets.newHashSet();
    for (Country c : Country.values()) {
      assertFalse(codes.contains(c.getIso2LetterCode()));
      assertFalse(codes.contains(c.getIso3LetterCode()));
      
      codes.add(c.getIso2LetterCode());
      codes.add(c.getIso3LetterCode());
      
      if (c.getIsoNumericalCode() != null) {
        assertFalse(codes.contains(c.getIsoNumericalCode().toString()));
        codes.add(c.getIsoNumericalCode().toString());
      }
    }
  }
  
  @Test
  public void testTitleUniqueness() {
    Set<String> names = Sets.newHashSet();
    for (Country c : Country.values()) {
      assertFalse(names.contains(c.getTitle()));
      names.add(c.getTitle());
    }
  }
  
}
