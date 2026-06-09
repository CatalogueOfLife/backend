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
package life.catalogue.api.vocab.area;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import static org.junit.Assert.*;

public class CountryTest {
  
  @Test
  public void testFromIsoCode() throws Exception {
    Assert.assertEquals(Country.ARGENTINA, Country.fromIsoCode("ar").get());
    assertEquals(Country.ARGENTINA, Country.fromIsoCode("AR").get());
  }

  @Test
  public void testLink() throws Exception {
    Assert.assertEquals(URI.create("https://www.iso.org/obp/ui/#iso:code:3166:AI"), Country.ANGUILLA.getLink());
    for (Country c : Country.values()) {
      assertNotNull(c.getLink());
    }
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
      // Kosovo is an exception
      if (l.isOfficial() && Country.KOSOVO != l) {
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
    assertEquals(250, officialCountries);
  }
  
  @Test
  public void testgetTitle() throws Exception {
    for (Country l : Country.values()) {
      assertNotNull(l.getName());
      assertTrue(l.getName().length() > 2);
    }
    assertEquals("United Kingdom", Country.UNITED_KINGDOM.getName());
    assertEquals("Germany", Country.GERMANY.getName());
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
  
  /**
   * Compares the Country enum against the Debian iso-codes ISO 3166-1 JSON,
   * which mirrors the official ISO list. Fails if codes do not match 1:1
   * (modulo deliberately user-assigned codes not in ISO, e.g. Kosovo), if
   * alpha-3 / numeric codes diverge, or if the number of name differences
   * grows beyond the currently accepted set.
   */
  @Test
  public void testIsoCodes3166_1() throws Exception {
    // codes that are intentionally in the enum but not in official ISO 3166-1
    Set<String> nonIsoEnumCodes = Set.of("XK"); // Kosovo (user-assigned code)
    int expectedNameDiffs = 8;

    URI url = URI.create("https://salsa.debian.org/iso-codes-team/iso-codes/-/raw/main/data/iso_3166-1.json");
    JsonNode root;
    try (InputStream in = url.toURL().openStream()) {
      root = new ObjectMapper().readTree(in);
    }
    JsonNode arr = root.get("3166-1");
    assertNotNull("missing '3166-1' field in " + url, arr);
    assertTrue("empty '3166-1' list", arr.size() > 0);

    Map<String, JsonNode> iso = new TreeMap<>();
    for (JsonNode n : arr) {
      iso.put(n.get("alpha_2").asText().toUpperCase(), n);
    }

    Map<String, Country> enumByCode = new HashMap<>();
    for (Country c : Country.values()) {
      if (c.isOfficial() && !nonIsoEnumCodes.contains(c.getIso2LetterCode())) {
        enumByCode.put(c.getIso2LetterCode(), c);
      }
    }

    // exclude the deliberate non-ISO additions from the ISO side too, so the
    // test stays green whether or not ISO ever adopts them in the future
    Set<String> isoCodes = new TreeSet<>(iso.keySet());
    isoCodes.removeAll(nonIsoEnumCodes);

    Set<String> overlap = new TreeSet<>(enumByCode.keySet());
    overlap.retainAll(isoCodes);

    Set<String> missingInEnum = new TreeSet<>(isoCodes);
    missingInEnum.removeAll(enumByCode.keySet());

    Set<String> missingInIso = new TreeSet<>(enumByCode.keySet());
    missingInIso.removeAll(isoCodes);

    Map<String, String> alpha3Diffs = new TreeMap<>();
    Map<String, String> numericDiffs = new TreeMap<>();
    Map<String, String> nameDiffs = new TreeMap<>();
    for (String code : overlap) {
      Country c = enumByCode.get(code);
      JsonNode n = iso.get(code);
      String isoA3 = n.get("alpha_3").asText();
      int isoNum = Integer.parseInt(n.get("numeric").asText());
      String isoName = n.get("name").asText();
      if (!c.getIso3LetterCode().equals(isoA3)) {
        alpha3Diffs.put(code, "enum=" + c.getIso3LetterCode() + " iso=" + isoA3);
      }
      if (c.getIsoNumericalCode() == null || c.getIsoNumericalCode() != isoNum) {
        numericDiffs.put(code, "enum=" + c.getIsoNumericalCode() + " iso=" + isoNum);
      }
      if (!c.getName().equals(isoName)) {
        nameDiffs.put(code, "enum=\"" + c.getName() + "\" iso=\"" + isoName + "\"");
      }
    }

    System.out.println("=== Country enum vs Debian iso-codes ISO 3166-1 ===");
    System.out.println("source: " + url);
    System.out.println("Official enum countries: " + enumByCode.size());
    System.out.println("ISO countries:           " + iso.size());
    System.out.println("Overlap (by alpha-2):    " + overlap.size());
    System.out.println("Missing in enum:         " + missingInEnum.size());
    for (String code : missingInEnum) {
      JsonNode n = iso.get(code);
      System.out.println("  + " + code + " (" + n.get("alpha_3").asText() + "/" + n.get("numeric").asText() + ") " + n.get("name").asText());
    }
    System.out.println("Missing in ISO:          " + missingInIso.size());
    for (String code : missingInIso) {
      Country c = enumByCode.get(code);
      System.out.println("  - " + code + " (" + c.getIso3LetterCode() + "/" + c.getIsoNumericalCode() + ") " + c.getName());
    }
    System.out.println("Alpha-3 differences:     " + alpha3Diffs.size());
    alpha3Diffs.forEach((k, v) -> System.out.println("  ~ " + k + "\t" + v));
    System.out.println("Numeric differences:     " + numericDiffs.size());
    numericDiffs.forEach((k, v) -> System.out.println("  ~ " + k + "\t" + v));
    System.out.println("Name differences:        " + nameDiffs.size());
    nameDiffs.forEach((k, v) -> System.out.println("  ~ " + k + "\t" + v));

    // strict assertions (Kosovo / other nonIsoEnumCodes are filtered out on both sides)
    assertEquals("codes in ISO but not in enum: " + missingInEnum, Set.of(), missingInEnum);
    assertEquals("codes in enum but not in ISO: " + missingInIso, Set.of(), missingInIso);
    assertEquals("alpha-3 mismatches: " + alpha3Diffs, 0, alpha3Diffs.size());
    assertEquals("numeric mismatches: " + numericDiffs, 0, numericDiffs.size());
    assertTrue("name differences grew from " + expectedNameDiffs + " to " + nameDiffs.size()
            + " — new diffs: " + nameDiffs,
        nameDiffs.size() <= expectedNameDiffs);
  }

  @Test
  public void testTitleUniqueness() {
    Set<String> names = Sets.newHashSet();
    for (Country c : Country.values()) {
      assertFalse(names.contains(c.getName()));
      names.add(c.getName());
    }
  }
  
}
