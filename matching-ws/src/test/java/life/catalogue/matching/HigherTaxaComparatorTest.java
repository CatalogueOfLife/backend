/*
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
package life.catalogue.matching;

import life.catalogue.matching.util.Dictionaries;
import life.catalogue.matching.util.HigherTaxaComparator;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** @author markus */
public class HigherTaxaComparatorTest {

  @Test
  public void testReloading() throws IOException {
    HigherTaxaComparator htl = new HigherTaxaComparator(Dictionaries.createDefault());
    htl.loadClasspathDicts("dicts");
    assertTrue(htl.size() > 10);
  }

  @Test
  public void testKingdoms() throws IOException {
    HigherTaxaComparator htl = new HigherTaxaComparator(Dictionaries.createDefault());
    htl.loadClasspathDicts("dicts");
    // Animalia varieties
    assertEquals("Animalia", htl.lookup("Animalia", Rank.KINGDOM));
    assertEquals("Animalia", htl.lookup("Anamalia", Rank.KINGDOM));
    assertEquals("Animalia", htl.lookup("Animal", Rank.KINGDOM));
    assertEquals("Animalia", htl.lookup("Metazoa", Rank.KINGDOM));

    assertNull(htl.lookup("Incertae sedis", Rank.KINGDOM));
  }

  @Test
  public void testBlacklist() throws IOException {
    HigherTaxaComparator htl = new HigherTaxaComparator(Dictionaries.createDefault());
    htl.loadClasspathDicts("dicts");

    assertFalse(htl.isBlacklisted("Animals"));
    assertFalse(htl.isBlacklisted("Abies indeterminata"));
    assertTrue(htl.isBlacklisted("Unknown"));
    assertTrue(htl.isBlacklisted("Incertae sedis"));
  }

  @Test
  public void testNormalization() throws IOException {
    assertEquals("ANIMALS", HigherTaxaComparator.norm("Animals"));
    assertEquals("ANIMALS", HigherTaxaComparator.norm("  Animals"));
    assertEquals("ANIMALS", HigherTaxaComparator.norm("12Animals???"));
    assertEquals("DRECKS ASTERACEAE", HigherTaxaComparator.norm("drecks Asteraceae"));
    assertNull(HigherTaxaComparator.norm(null));
    assertNull(HigherTaxaComparator.norm(" "));
    assertNull(HigherTaxaComparator.norm("321"));
    assertNull(HigherTaxaComparator.norm(""));
    assertNull(HigherTaxaComparator.norm(",.-öä? "));
  }
}
