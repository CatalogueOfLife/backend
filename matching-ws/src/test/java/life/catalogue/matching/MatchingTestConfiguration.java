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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.gbif.nameparser.api.NameParser;
import org.gbif.utils.file.InputStreamUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Guice module setting up all dependencies to expose the NubMatching service. */
@Configuration
@ComponentScan(basePackages = {"life.catalogue.matching"})
public class MatchingTestConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(MatchingTestConfiguration.class);

  @Bean
  public static DatasetIndex provideIndex() throws IOException {
    return DatasetIndex.newMemoryIndex(loadIndexJson());
  }

  @Bean
  public static HigherTaxaComparator provideSynonyms() throws IOException {
    LOG.info("Loading synonym dictionaries from classpath ...");
    HigherTaxaComparator syn = new HigherTaxaComparator();
    syn.loadClasspathDicts("dicts");
    return syn;
  }

  @Bean
  public NameParser provideParser() {
    return NameParsers.INSTANCE;
  }

  /**
   * Load all nubXX.json files from the index resources into a distinct list of NameUsage instances.
   * The individual nubXX.json files are regular results of a NameUsageMatch and can be added to the
   * folder to be picked up here.
   */
  private static List<NameUsage> loadIndexJson() {
    Map<String, NameUsage> usages = Maps.newHashMap();

    InputStreamUtils isu = new InputStreamUtils();
    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    int id = 1;
    while (id < 300) {
      String file = "index/nub" + id + ".json";
      InputStream json = isu.classpathStream(file);
      if (json != null) {
        try {
          int before = usages.size();
          NameUsageMatchV1 m = mapper.readValue(json, NameUsageMatchV1.class);
          for (NameUsage u : extractUsages(m)) {
            if (u != null) {
              usages.put(u.getId(), u);
            }
          }
          System.out.println("Loaded " + (usages.size() - before) + " new usage(s) from " + file);
        } catch (IOException e) {
          Assertions.fail("Failed to read " + file + ": " + e.getMessage());
        }
      }
      id++;
    }
    return Lists.newArrayList(usages.values());
  }

  private static List<NameUsage> extractUsages(NameUsageMatchV1 m) {
    List<NameUsage> usages = Lists.newArrayList();

    NameUsage u = new NameUsage();
    u.setScientificName(m.getScientificName());
    u.setId(m.getUsageKey().toString());

    usages.add(u);
    if (m.getAlternatives() != null) {
      m.getAlternatives().stream()
          .forEach(
              a -> {
                NameUsage alt = new NameUsage();
                alt.setScientificName(a.getScientificName());
                alt.setId(a.getUsageKey().toString());
                usages.add(alt);
              });
    }
    return usages;
  }
}
