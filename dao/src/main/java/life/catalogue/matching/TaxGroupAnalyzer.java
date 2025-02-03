package life.catalogue.matching;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.collection.CountEnumMap;
import life.catalogue.parser.TaxGroupParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import static life.catalogue.api.vocab.TaxGroup.*;

public class TaxGroupAnalyzer {
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupAnalyzer.class);
  private static final TaxGroupParser parser = TaxGroupParser.PARSER;
  private static final Pattern YEAR_PATTERN = Pattern.compile("([12]\\d{3})");
  private static final Pattern BAS_COMB_PATTERN = Pattern.compile("\\(\\s*[A-Z].+\\)[^()]*[A-Z]");
  // map of suffix to groups, sorted by suffix length
  // ImmutableMap keeps insertion order
  private static final ImmutableMap<String,Map<Rank,TaxGroup[]>> SUFFICES = new ImmutableMap.Builder<String, Map<Rank,TaxGroup[]>>()
      // Prokaryotes
      .put("viricetidae", Map.of(Rank.SUBCLASS, new TaxGroup[]{Viruses}))
      .put("viricotina", Map.of(Rank.SUBPHYLUM, new TaxGroup[]{Viruses}))
      .put("viricetes", Map.of(Rank.CLASS, new TaxGroup[]{Viruses}))
      .put("mycetidae", Map.of(Rank.SUBCLASS, new TaxGroup[]{Fungi}))
      .put("mycotina", Map.of(Rank.SUBPHYLUM, new TaxGroup[]{Fungi}))
      .put("phycidae", Map.of(Rank.SUBCLASS, new TaxGroup[]{Algae}))
      .put("viricota", Map.of(Rank.PHYLUM, new TaxGroup[]{Viruses}))
      .put("virineae", Map.of(Rank.SUBORDER, new TaxGroup[]{Viruses}))
      .put("virinae", Map.of(Rank.SUBFAMILY, new TaxGroup[]{Viruses}))
      .put("viridae", Map.of(Rank.FAMILY, new TaxGroup[]{Viruses}))
      .put("virites", Map.of(Rank.SUBKINGDOM, new TaxGroup[]{Viruses}))
      .put("phyceae", Map.of(Rank.CLASS, new TaxGroup[]{Algae}))
      .put("phytina", Map.of(Rank.SUBPHYLUM, new TaxGroup[]{Plants, Algae}))
      .put("virales", Map.of(Rank.ORDER, new TaxGroup[]{Viruses}))
      .put("mycetes", Map.of(Rank.CLASS, new TaxGroup[]{Fungi}))
      .put("iformes", Map.of(Rank.ORDER, new TaxGroup[]{Animals}))
      .put("oideae", Map.of(Rank.SUBFAMILY, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}))
      .put("ophyta", Map.of(Rank.PHYLUM, new TaxGroup[]{Plants, Algae}))
      .put("opsida", Map.of(Rank.CLASS, new TaxGroup[]{Plants}))
      .put("mycota", Map.of(Rank.PHYLUM, new TaxGroup[]{Fungi}))
      .put("aceae", Map.of(Rank.FAMILY, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}))
      .put("oidae", Map.of(Rank.EPIFAMILY, new TaxGroup[]{Animals}))
      .put("oidea", Map.of(Rank.SUPERFAMILY, new TaxGroup[]{Animals}))
      .put("virus", Map.of(
        Rank.GENUS, new TaxGroup[]{Viruses},
        Rank.SUBGENUS, new TaxGroup[]{Viruses}
      ))
      .put("viria", Map.of(Rank.REALM, new TaxGroup[]{Viruses}))
      .put("virae", Map.of(Rank.KINGDOM, new TaxGroup[]{Viruses}))
      .put("ineae", Map.of(Rank.SUBORDER, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}))
      .put("acea", Map.of(Rank.SUPERFAMILY, new TaxGroup[]{Plants, Algae, Fungi}))
      .put("ales", Map.of(Rank.ORDER, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}))
      .put("anae", Map.of(Rank.SUPERORDER, new TaxGroup[]{Plants, Algae, Fungi}))
      .put("aria", Map.of(Rank.INFRAORDER, new TaxGroup[]{Plants, Algae, Fungi}))
      .put("idae", Map.of(
        Rank.FAMILY, new TaxGroup[]{Animals},
        Rank.SUBCLASS, new TaxGroup[]{Prokaryotes, Plants}
      ))
      .put("inae", Map.of(
        Rank.SUBFAMILY, new TaxGroup[]{Animals},
        Rank.SUBTRIBE, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}
      ))
      .put("vira", Map.of(Rank.SUBREALM, new TaxGroup[]{Viruses}))
      .put("eae", Map.of(Rank.TRIBE, new TaxGroup[]{Prokaryotes, Plants, Algae, Fungi}))
      .put("ida", Map.of(Rank.ORDER, new TaxGroup[]{Animals}))
      .put("ina", Map.of(Rank.SUBTRIBE, new TaxGroup[]{Animals}))
      .put("ini", Map.of(Rank.TRIBE, new TaxGroup[]{Animals}))
      .put("odd", Map.of(Rank.INFRAFAMILY, new TaxGroup[]{Animals}))
      .build();

  public TaxGroup analyze(SimpleName name) {
    return analyze(name, List.of());
  }

  /**
   * Tries to figure out the taxonomic group this name with classification belongs to.
   * If nothing can be found, null is returned for unknown.
   */
  public TaxGroup analyze(SimpleName name, Collection<? extends SimpleName> classification) {
    CountEnumMap<TaxGroup> groups = new CountEnumMap<>(TaxGroup.class);
    try {
      var pg = parser.parse(name.getName());
      pg.ifPresent(groups::inc);
      for (var sn : classification) {
        pg = parser.parse(sn.getName());
        pg.ifPresent(groups::inc);
      }
    } catch (UnparsableException e) {
      LOG.error("Error analyzing taxonomic group", e);
    }

    // add group based on code if there was no group spotted so far at all.
    // we should only add a single group here, cause we need to select a single one at the end anyways
    NomCode code = name.getCode();
    if (code != null && groups.isEmpty()) {
      switch (code) {
        case BACTERIAL:
          groups.inc(Prokaryotes);
          break;
        case ZOOLOGICAL:
        case BOTANICAL:
          groups.inc(Eukaryotes);
          break;
        case VIRUS:
          groups.inc(TaxGroup.Viruses);
          break;
        case CULTIVARS:
          groups.inc(TaxGroup.Angiosperms);
          break;
      }
    }

    while (!groups.isEmpty()) {
      var result = filterCandidates(groups);
      if (result != null) {
        return result;
      }
      // compare all groups with each other
      // and remove the group which contradicts most
      CountEnumMap<TaxGroup> grpMatches = new CountEnumMap<>(TaxGroup.class);
      for (var g : groups.keySet()) {
        for (var g2 : groups.keySet()) {
          if (!g.isDisparateTo(g2)) {
            grpMatches.inc(g);
          }
        }
      }
      final int highest = grpMatches.highestCount().get();
      if (!groups.removeIf(g -> grpMatches.get(g) < highest)) {
        // we could not reduce the groups any more, pick root
        // if we have more than 1 group still we have a contradiction... count by root group and select the lowest group of the largest set
        CountEnumMap<TaxGroup> counts = new CountEnumMap<>(TaxGroup.class);
        for (var g : groups.keySet()) {
          counts.inc(g);
          for (var p : g.classification()) {
            counts.inc(p);
          }
        }
        return counts.highest();
      }
    }
    return null;
  }

  private TaxGroup filterCandidates(CountEnumMap<TaxGroup> groups) {
    if (!groups.isEmpty()) {
      // keep lowest groups only. Exclude groups which are implicit in parents
      Set<TaxGroup> distinctRoots = new HashSet<>(groups.keySet());
      distinctRoots.removeIf(g -> {
        for (var other : groups.keySet()) {
          if (g==other) continue;
          if (g.contains(other)) {
            return true;
          }
        }
        return false;
      });

      if (distinctRoots.size() == 1) {
        return distinctRoots.iterator().next();
      }
    }
    return null;
  }
  @VisibleForTesting
  protected static Optional<NomCode> detectCodeFromAuthorship(SimpleName sn) {
    if (sn.getAuthorship() != null) {
      if (YEAR_PATTERN.matcher(sn.getAuthorship()).find()) {
        return Optional.of(NomCode.ZOOLOGICAL);
      } else if (BAS_COMB_PATTERN.matcher(sn.getAuthorship()).find()) {
        return Optional.of(NomCode.BOTANICAL);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  protected static Optional<NomCode> detectCodeFromRank(SimpleName sn) {
    if (sn.getRank() != null) {
      return Optional.ofNullable(sn.getRank().isRestrictedToCode());
    }
    return Optional.empty();
  }

  @VisibleForTesting
  protected static Optional<TaxGroup[]> detectGroupFromSuffix(SimpleName sn) {
    if (sn.getRank() != null && sn.getRank() != Rank.UNRANKED) {
      for (var suff : SUFFICES.entrySet()) {
        if (sn.getName().endsWith(suff.getKey()) && suff.getValue().containsKey(sn.getRank())) {
          return Optional.of(suff.getValue().get(sn.getRank()));
        }
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  protected NomCode detectCode(SimpleName name, Collection<? extends SimpleName> classification) {
    // we count number of hints for the code to finally decide which it is
    CountEnumMap<NomCode> counter = new CountEnumMap<>(NomCode.class);

    // authorship -> code
    detectCodeFromAuthorship(name).ifPresent(c -> counter.inc(c, 2));
    detectCodeFromRank(name).ifPresent(c -> counter.inc(c, 5));
    if (classification != null) {
      classification.forEach(sn -> {
        detectCodeFromAuthorship(sn).ifPresent(c -> counter.inc(c, 1));
        detectCodeFromRank(sn).ifPresent(c -> counter.inc(c, 2));
      });
    }
    return counter.highest();
  }

  @VisibleForTesting
  protected TaxGroup groupBySuffix(SimpleName name, Collection<? extends SimpleName> classification) {
    // we count number of hints for the code to finally decide which it is
    CountEnumMap<TaxGroup> counter = new CountEnumMap<>(TaxGroup.class);

    detectGroupFromSuffix(name).ifPresent(grps -> {
      for (var g : grps) {
        counter.inc(g);
      }
    });

    if (classification != null) {
      classification.forEach(sn -> detectGroupFromSuffix(sn).ifPresent(grps -> {
        for (var g : grps) {
          counter.inc(g);
        }
      }));
    }
    return counter.highest();
  }
}
