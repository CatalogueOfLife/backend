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

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.index.DatasetIndex;
import life.catalogue.matching.model.NameUsage;
import life.catalogue.matching.model.NameUsageMatch;
import life.catalogue.matching.model.v1.NameUsageMatchFlatV1;
import life.catalogue.matching.model.v1.NameUsageMatchV1;
import life.catalogue.matching.service.IndexingService;
import life.catalogue.matching.util.Dictionaries;
import life.catalogue.matching.util.HigherTaxaComparator;
import life.catalogue.matching.util.IOUtils;
import life.catalogue.matching.util.NameParsers;

import org.gbif.nameparser.api.NameParser;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.api.UnparsableNameException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/** Guice module setting up all dependencies to expose the NubMatching service. */
@Configuration
@ComponentScan(basePackages = {"life.catalogue.matching"})
public class MatchingTestConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(MatchingTestConfiguration.class);

  @Bean
  public static DatasetIndex provideIndex() throws IOException {
    Directory dir = IndexingService.newMemoryIndex(loadIndexFromV1Responses(), loadIndexFromV2Responses());
    return DatasetIndex.newDatasetIndex(dir);
  }

  @Bean
  public static HigherTaxaComparator provideSynonyms() throws IOException {
    LOG.info("Loading synonym dictionaries from classpath ...");
    HigherTaxaComparator syn = new HigherTaxaComparator(Dictionaries.createDefault());
    syn.loadClasspathDicts("dicts");
    return syn;
  }

  @Bean
  public NameParser provideParser() {
    return NameParsers.INSTANCE;
  }

  /**
   * Load all nubXX.json files from the index resources into a distinct list of NameUsage instances.
   * The individual nubXX.json files are regular results of a NameUsageMatch (v1) and can be added
   * to the folder to be picked up here.
   */
  private static List<NameUsage> loadIndexFromV1Responses() {
    Map<String, NameUsage> usages = Maps.newHashMap();

    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    int id = 1;

    try (FileWriter writer = new FileWriter("/tmp/testv1.csv")) {
      while (id < 300) {
        String file = "index/nub" + id + ".json";
        InputStream json = IOUtils.classpathStream(file);
        if (json != null) {
          try {
            NameUsageMatchFlatV1 m = mapper.readValue(json, NameUsageMatchFlatV1.class);
            for (NameUsage u : extractUsagesFromV1Responses(m)) {
              if (u != null) {
                NameUsage existing = usages.get(u.getId());
                if (existing == null) {
                  usages.put(u.getId(), u);
                } else {
                  if (existing.getAuthorship() == null && u.getAuthorship() != null) {
                    usages.put(u.getId(), u);
                  }
                }
              }
            }
          } catch (IOException e) {
            Assertions.fail("Failed to read " + file + ": " + e.getMessage());
          }
        }
        id++;
      }
      for (NameUsage u : usages.values()) {
        writer.write(
            u.getId()
                + ","
                + u.getScientificName()
                + ","
                + u.getAuthorship()
                + ","
                + u.getRank()
                + ","
                + u.getStatus()
                + ","
                + u.getParentId()
                + "\n");
      }
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Lists.newArrayList(usages.values());
  }

  /**
   * Load all matchXX.json files from the index resources into a distinct list of NameUsage instances.
   * The individual matchXX.json files are regular results of a NameUsageMatch (v2) and can be added
   * to the folder to be picked up here.
   */
  private static List<NameUsage> loadIndexFromV2Responses() {
    Map<String, NameUsage> usages = Maps.newHashMap();

    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    int id = 1;

    try (FileWriter writer = new FileWriter("/tmp/testv2.csv")) {
      while (id < 10) {
        String file = "index/match" + id + ".json";
        InputStream json = IOUtils.classpathStream(file);
        if (json != null) {
          try {
            NameUsageMatch m = mapper.readValue(json, NameUsageMatch.class);
            for (NameUsage u : extractUsagesFromV2Responses(m)) {
              if (u != null) {
                NameUsage existing = usages.get(u.getId());
                if (existing == null) {
                  usages.put(u.getId(), u);
                } else {
                  if (existing.getAuthorship() == null && u.getAuthorship() != null) {
                    usages.put(u.getId(), u);
                  }
                }
              }
            }
          } catch (IOException e) {
            Assertions.fail("Failed to read " + file + ": " + e.getMessage());
          }
        }
        id++;
      }
      for (NameUsage u : usages.values()) {
        writer.write(
          u.getId()
            + ","
            + u.getScientificName()
            + ","
            + u.getAuthorship()
            + ","
            + u.getRank()
            + ","
            + u.getStatus()
            + ","
            + u.getParentId()
            + "\n");
      }
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Lists.newArrayList(usages.values());
  }

  private static NameUsage toNU(NameUsageMatch m, String parentID) {
    var nb = toNU(m.getUsage(), parentID);
    if (m.isSynonym()) {
      nb.status(TaxonomicStatus.SYNONYM.name());
    }
    return nb.build();
  }
  private static NameUsage.NameUsageBuilder toNU(NameUsageMatch.Usage u, String parentID) {
    return NameUsage.builder()
      .id(u.getKey())
      .parentId(parentID)
      .scientificName(u.getName())
      .authorship(u.getAuthorship())
      .rank(u.getRank().name())
      .status(TaxonomicStatus.ACCEPTED.name());
  }
  private static NameUsage toNU(NameUsageMatch.RankedName u, String parentID) {
    return NameUsage.builder()
      .id(u.getKey())
      .parentId(parentID)
      .scientificName(u.getName())
      .rank(u.getRank().name())
      .status(TaxonomicStatus.ACCEPTED.name())
      .build();
  }

  private static Map<String, NameUsage> extractUsagesNoDiagnostics(NameUsageMatch m) {
    Map<String, NameUsage> usages = new HashMap<>();
    LinkedList<NameUsageMatch.RankedName> classification = m.getClassification() == null ? new LinkedList<>() : new LinkedList<>(m.getClassification());
    String pid = null;

    // first entire classification as it has the least details and can be overwritten below
    for (var cl : classification) {
      usages.put(cl.getKey(), toNU(cl, pid));
      pid = cl.getKey();
    }

    if (m.getAcceptedUsage() != null) {
      pid = usages.get(m.getAcceptedUsage().getKey()).getParentId();
      usages.put(m.getAcceptedUsage().getKey(), toNU(m.getAcceptedUsage(), pid).build());
      pid = m.getAcceptedUsage().getKey();
    }

    if (m.getUsage() != null) {
      pid = m.getAcceptedUsage() != null ?
        m.getAcceptedUsage().getKey() :
        usages.get(m.getUsage().getKey()).getParentId();
      usages.put(m.getUsage().getKey(), toNU(m, pid));
    }

    return usages;
  }

  private static List<NameUsage> extractUsagesFromV2Responses(NameUsageMatch m) {
    Map<String, NameUsage> usages = extractUsagesNoDiagnostics(m);

    if (m.getDiagnostics() != null && m.getDiagnostics().getAlternatives() != null) {
      m.getDiagnostics().getAlternatives().forEach(a -> {
            usages.putAll(extractUsagesNoDiagnostics(a));
      });
    }
    return usages.values().stream().collect(Collectors.toUnmodifiableList());
  }

  /**
   * Translate a NameUsageMatchV1 into a list of NameUsage instances.
   *
   * @return a list of NameUsage instances, including the main usage and all alternatives.
   */
  private static List<NameUsage> extractUsagesFromV1Responses(NameUsageMatchFlatV1 m) {
    Map<String, NameUsage> usages = new HashMap<>();

    NameUsage u = NameUsage.builder().build();
    u.setScientificName(
        m.getCanonicalName() != null && !isViralName(m.getScientificName())
            ? m.getCanonicalName()
            : m.getScientificName());
    if (m.getCanonicalName() != null
        && m.getScientificName() != null
        && m.getScientificName().length() > m.getCanonicalName().length()
        && !isViralName(m.getScientificName())) {
      u.setAuthorship(m.getScientificName().substring(m.getCanonicalName().length() + 1));
    }
    if (m.getUsageKey() != null) {
      u.setId(m.getUsageKey().toString());
    }

    u.setRank(m.getRank());
    setStatus(m, u);
    setParent(m, u);
    usages.put(u.getId(), u);

    // add all the intermediate ranks
    if (m.getKingdomKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getKingdomKey().toString())
              .rank(Rank.KINGDOM.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .scientificName(m.getKingdom())
              .build());
    if (m.getPhylumKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getPhylumKey().toString())
              .rank(Rank.PHYLUM.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.PHYLUM))
              .scientificName(m.getPhylum())
              .build());
    if (m.getClassKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getClassKey().toString())
              .rank(Rank.CLASS.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.CLASS))
              .scientificName(m.getClazz())
              .build());
    if (m.getOrderKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getOrderKey().toString())
              .rank(Rank.ORDER.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.ORDER))
              .scientificName(m.getOrder())
              .build());
    if (m.getFamilyKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getFamilyKey().toString())
              .rank(Rank.FAMILY.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.FAMILY))
              .scientificName(m.getFamily())
              .build());
    if (m.getGenusKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getGenusKey().toString())
              .rank(Rank.GENUS.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.GENUS))
              .scientificName(m.getGenus())
              .build());
    if (m.getSpeciesKey() != null)
      addIfNotPresent(
          usages,
          NameUsage.builder()
              .id(m.getSpeciesKey().toString())
              .rank(Rank.SPECIES.name())
              .status(TaxonomicStatus.ACCEPTED.toString())
              .parentId(getParentKey(m, Rank.SPECIES))
              .scientificName(m.getSpecies())
              .build());

    if (m.getAlternatives() != null) {
      m.getAlternatives()
          .forEach(
              a -> {
                NameUsage alt = NameUsage.builder().build();
                alt.setId(a.getUsageKey().toString());
                alt.setScientificName(
                    a.getCanonicalName() != null && !isViralName(a.getScientificName())
                        ? a.getCanonicalName()
                        : a.getScientificName());
                if (a.getCanonicalName() != null
                    && a.getScientificName() != null
                    && a.getScientificName().length() > a.getCanonicalName().length()
                    && !isViralName(a.getScientificName())) {
                  alt.setAuthorship(
                      a.getScientificName().substring(a.getCanonicalName().length() + 1));
                }
                alt.setRank(a.getRank());
                setStatus(a, alt);
                setParent(a, alt);
                usages.put(alt.getId(), alt);

                // add all the intermediate ranks
                if (a.getKingdomKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getKingdomKey().toString())
                          .rank(Rank.KINGDOM.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .scientificName(a.getKingdom())
                          .build());
                if (a.getPhylumKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getPhylumKey().toString())
                          .rank(Rank.PHYLUM.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.PHYLUM))
                          .scientificName(a.getPhylum())
                          .build());
                if (a.getClassKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getClassKey().toString())
                          .rank(Rank.CLASS.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.CLASS))
                          .scientificName(a.getClazz())
                          .build());
                if (a.getOrderKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getOrderKey().toString())
                          .rank(Rank.ORDER.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.ORDER))
                          .scientificName(a.getOrder())
                          .build());
                if (a.getFamilyKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getFamilyKey().toString())
                          .rank(Rank.FAMILY.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.FAMILY))
                          .scientificName(a.getFamily())
                          .build());
                if (a.getGenusKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getGenusKey().toString())
                          .rank(Rank.GENUS.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.GENUS))
                          .scientificName(a.getGenus())
                          .build());
                if (a.getSpeciesKey() != null)
                  addIfNotPresent(
                      usages,
                      NameUsage.builder()
                          .id(a.getSpeciesKey().toString())
                          .rank(Rank.SPECIES.name())
                          .status(TaxonomicStatus.ACCEPTED.toString())
                          .parentId(getParentKey(a, Rank.SPECIES))
                          .scientificName(a.getSpecies())
                          .build());
              });
    }
    return usages.values().stream().collect(Collectors.toUnmodifiableList());
  }

  public static boolean isViralName(String name) {
    try {
      NameParsers.INSTANCE.parse(name, null);
    } catch (UnparsableNameException e) {
      if (NameType.VIRUS == e.getType()) {
        return true;
      }
    } catch (InterruptedException e) {
      // swallow
    }
    return false;
  }

  public static void addIfNotPresent(Map<String, NameUsage> usages, NameUsage usage) {
    if (!usages.containsKey(usage.getId())) {
      usages.put(usage.getId(), usage);
    }
  }

  private static void setStatus(NameUsageMatchFlatV1 source, NameUsage target) {
    if (source.getSynonym()) {
      target.setStatus(TaxonomicStatus.SYNONYM.toString());
    } else {
      if (source.getStatus() != null && source.getStatus().equals(NameUsageMatchV1.TaxonomicStatusV1.DOUBTFUL)) {
        target.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED.toString());
      } else {
        target.setStatus(TaxonomicStatus.ACCEPTED.toString());
      }
    }
  }

  private static String getParentKey(NameUsageMatchFlatV1 m, Rank aboveRank) {
    if (aboveRank.ordinal() > Rank.SPECIES.ordinal()
        && m.getSpeciesKey() != null
        && !m.getUsageKey().equals(m.getSpeciesKey())
        && !m.getGenusKey().equals(m.getAcceptedUsageKey())) return m.getSpeciesKey().toString();
    if (aboveRank.ordinal() > Rank.GENUS.ordinal()
        && m.getGenusKey() != null
        && !m.getUsageKey().equals(m.getGenusKey())
        && !m.getGenusKey().equals(m.getAcceptedUsageKey())) return m.getGenusKey().toString();
    if (aboveRank.ordinal() > Rank.FAMILY.ordinal()
        && m.getFamilyKey() != null
        && !m.getUsageKey().equals(m.getFamilyKey())
        && !m.getFamilyKey().equals(m.getAcceptedUsageKey())) return m.getFamilyKey().toString();
    if (aboveRank.ordinal() > Rank.ORDER.ordinal()
        && m.getOrderKey() != null
        && !m.getUsageKey().equals(m.getOrderKey())
        && !m.getOrderKey().equals(m.getAcceptedUsageKey())) return m.getOrderKey().toString();
    if (aboveRank.ordinal() > Rank.CLASS.ordinal()
        && m.getClassKey() != null
        && !m.getUsageKey().equals(m.getClassKey())
        && !m.getClassKey().equals(m.getAcceptedUsageKey())) return m.getClassKey().toString();
    if (aboveRank.ordinal() > Rank.PHYLUM.ordinal()
        && m.getPhylumKey() != null
        && !m.getUsageKey().equals(m.getPhylumKey())
        && !m.getPhylumKey().equals(m.getAcceptedUsageKey())) return m.getPhylumKey().toString();
    if (aboveRank.ordinal() > Rank.KINGDOM.ordinal()
        && m.getKingdomKey() != null
        && !m.getUsageKey().equals(m.getKingdomKey())
        && !m.getKingdomKey().equals(m.getAcceptedUsageKey())) return m.getKingdomKey().toString();
    return null;
  }

  private static void setParent(NameUsageMatchFlatV1 m, NameUsage u) {

    if (m.getSynonym()) {

      if (m.getAcceptedUsageKey() != null) {
        u.setParentId(m.getAcceptedUsageKey().toString());
        return;
      }

      // need to get the key from the other
      if (m.getRank().equals("SPECIES") && m.getSpeciesKey() != null)
        u.setParentId(m.getSpeciesKey().toString());
      if (m.getRank().equals("GENUS") && m.getGenusKey() != null)
        u.setParentId(m.getGenusKey().toString());
      if (m.getRank().equals("FAMILY") && m.getFamilyKey() != null)
        u.setParentId(m.getFamilyKey().toString());
      if (m.getRank().equals("ORDER") && m.getOrderKey() != null)
        u.setParentId(m.getOrderKey().toString());
      if (m.getRank().equals("CLASS") && m.getClassKey() != null)
        u.setParentId(m.getClassKey().toString());
      if (m.getRank().equals("PHYLUM") && m.getPhylumKey() != null)
        u.setParentId(m.getPhylumKey().toString());

    } else if (m.getRank() != null && u.getParentId() == null) {

      Rank rank = Rank.valueOf(m.getRank().toUpperCase());
      if (rank.ordinal() == Rank.SUBSPECIES.ordinal()
          && u.getParentId() == null
          && m.getSpeciesKey() != null) {
        u.setParentId(m.getSpeciesKey().toString());
      }
      if (rank.ordinal() == Rank.SPECIES.ordinal()
          && u.getParentId() == null
          && m.getGenusKey() != null) {
        u.setParentId(m.getGenusKey().toString());
      }
      if (rank.ordinal() >= Rank.GENUS.ordinal()
          && u.getParentId() == null
          && m.getFamilyKey() != null) {
        u.setParentId(m.getFamilyKey().toString());
      }
      if (rank.ordinal() >= Rank.FAMILY.ordinal()
          && u.getParentId() == null
          && m.getOrderKey() != null) {
        u.setParentId(m.getOrderKey().toString());
      }
      if (rank.ordinal() >= Rank.ORDER.ordinal()
          && u.getParentId() == null
          && m.getClassKey() != null) {
        u.setParentId(m.getClassKey().toString());
      }
      if (rank.ordinal() >= Rank.CLASS.ordinal()
          && u.getParentId() == null
          && m.getPhylumKey() != null) {
        u.setParentId(m.getPhylumKey().toString());
      }
      if (rank.ordinal() >= Rank.PHYLUM.ordinal()
          && u.getParentId() == null
          && m.getKingdomKey() != null) {
        u.setParentId(m.getKingdomKey().toString());
      }
    }
  }
}
