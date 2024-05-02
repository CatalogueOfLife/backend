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

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import java.io.InputStream;
import java.util.List;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.Rank;
import org.gbif.utils.file.csv.CSVReader;
import org.gbif.utils.file.csv.CSVReaderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DatasetIndexTest {

  private static DatasetIndex index;

  @BeforeAll
  public static void buildMatcher() throws Exception {
    HigherTaxaComparator syn = new HigherTaxaComparator();
    syn.loadClasspathDicts("dicts");
    index = DatasetIndex.newMemoryIndex(readTestNames());
  }

  public static List<NameUsage> readTestNames() throws Exception {
    List<NameUsage> usages = Lists.newArrayList();
    // 1	2	Acanthophora Hulst, 1896	Geometridae	Lepidoptera	Insecta	Arthropoda	Animalia	GENUS
    try (InputStream testFile = Resources.getResource("testNames.txt").openStream()) {
      CSVReader reader = CSVReaderFactory.build(testFile, "UTF8", "\t", null, 0);
      while (reader.hasNext()) {
        String[] row = reader.next();
        NameUsage n = NameUsage.builder().build();
        n.setId(row[0]);
        n.setParentId(row[1]);
        n.setScientificName(row[2]);
        n.setRank(row[8]);
        n.setStatus(
            StringUtils.isNotBlank(row[1])
                ? TaxonomicStatus.SYNONYM.toString()
                : TaxonomicStatus.ACCEPTED.toString());
        usages.add(n);
      }

      Preconditions.checkArgument(usages.size() == 10, "Wrong number of test names");
    }
    return usages;
  }

  @Test
  public void testMatchByName() {
    final String abiesAlbaKey = "7";
    NameUsageMatch m = index.matchByUsageKey(abiesAlbaKey);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());
    assertEquals("Abies alba Mill.", m.getUsage().getName());
    assertEquals(Rank.SPECIES, m.getUsage().getRank());
    assertFalse(m.isSynonym());
    assertNull(m.getAcceptedUsage());

    m = index.matchByName("Abies alba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    m = index.matchByName("abies  alba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    m = index.matchByName("Abbies alba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    m = index.matchByName("abyes alba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    m = index.matchByName(" apies  alba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    // sciname soundalike filter enables this
    m = index.matchByName("Abies alllbbbbaaa", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    m = index.matchByName("Aebies allba", true, 2).get(0);
    assertEquals(abiesAlbaKey, m.getUsage().getKey());

    // fuzzy searches use a minPrefix=1
    assertTrue(index.matchByName("Obies alba", true, 2).isEmpty());
    assertTrue(index.matchByName("Abies elba", false, 2).isEmpty());

    // synonym matching
    m = index.matchByName("Picea abies", false, 2).get(0);
    assertTrue(m.isSynonym());
  }
}
