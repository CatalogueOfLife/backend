package life.catalogue.assembly;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.tax.RankUtils;
import life.catalogue.parser.TaxGroupParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public class TaxGroupAnalyzer {
  private static final Logger LOG = LoggerFactory.getLogger(TaxGroupAnalyzer.class);
  final TaxGroupParser parser = TaxGroupParser.PARSER;
  private static final Pattern YEAR_PATTERN = Pattern.compile("([12]\\d{3})");

  public TaxGroup analyze(SimpleName name) {
    return analyze(name, List.of());
  }

  public TaxGroup analyze(SimpleName name, Collection<? extends SimpleName> classification) {
    Set<TaxGroup> groups = new HashSet<>();
    try {
      var pg = parser.parse(name.getName());
      if (pg.isPresent()) {
        groups.add(pg.get());
      }
      for (var sn : classification) {
        pg = parser.parse(sn.getName());
        if (pg.isPresent()) {
          groups.add(pg.get());
        }
      }
    } catch (UnparsableException e) {
      LOG.error("Error analyzing taxonomic group", e);
    }

    // figure out code if missing
    NomCode code = name.getCode();
    if (code == null) {
      // rank -> code
      if (name.getRank() != null && name.getRank().isRestrictedToCode() != null) {
        code = name.getRank().isRestrictedToCode();

      // authorship -> code
      } else if (name.getAuthorship() != null && YEAR_PATTERN.matcher(name.getAuthorship()).find()) {
        code = NomCode.ZOOLOGICAL;
      }
      //TODO: look at name suffix, see private method in RankUtils
    }

    // add group based on code
    if (code != null) {
      if (groups.isEmpty()) {
        switch (code) {
          case BACTERIAL:
            groups.add(TaxGroup.Prokaryotes);
            break;
          case ZOOLOGICAL:
            groups.add(TaxGroup.Animals);
            break;
          case VIRUS:
            groups.add(TaxGroup.Viruses);
            break;
          case CULTIVARS:
            groups.add(TaxGroup.Angiosperms);
            break;
        }
      } else {
        // filter by code
        final var codeFinal = code;
        groups.removeIf(tg -> tg.getCode() != codeFinal);
      }
    }

    if (!groups.isEmpty()) {
      // exclude groups which are implicit in parents
      Set<TaxGroup> distinctRoots = new HashSet<>(groups);
      distinctRoots.removeIf(g -> {
        for (var other : groups) {
          if (g==other) continue;
          if (other.contains(g)) {
            return true;
          }
        }
        return false;
      });

      if (distinctRoots.size() == 1) {
        return distinctRoots.iterator().next();

      } else {
        // if we have more than 1 group still we have a contradiction... count by root group and select the lowest group of the largest set
        CountMap<TaxGroup> counts = new CountMap<>(TaxGroup.class);
        for (var g : groups) {
          counts.inc(g.root());
        }
        return counts.highest();
      }
    }
    return null;
  }
}
