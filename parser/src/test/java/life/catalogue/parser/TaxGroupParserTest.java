package life.catalogue.parser;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaxGroupParserTest extends ParserTestBase<TaxGroup> {
  Set<String> badNames = Set.of(
    "unamed",
    "unnamed",
    "incertae",
    "incertae sedis"
  );

  public TaxGroupParserTest() {
    super(TaxGroupParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TaxGroup.Arthropods, "Arthropoda");
    assertParse(TaxGroup.Arthropods, "arthropods");
    assertParse(TaxGroup.Animals, "animalia");
    assertParse(null, "anima");

    assertParse(TaxGroup.Chordates, "Falconidae");
    // recognize suffix
    assertParse(TaxGroup.Algae, "Blablaphyceae");
    assertParse(TaxGroup.Fungi, "Blablamycetes");
    assertParse(TaxGroup.Fungi, "Blablamycota");
    assertParse(TaxGroup.Fungi, "Blablamycotina");
    assertParse(TaxGroup.Plants, "Blablaphytina");
    // APG families and clades
    assertParse(TaxGroup.Angiosperms, "Lactoridaceae");
    assertParse(TaxGroup.Angiosperms, "rosids");
    assertParse(TaxGroup.Angiosperms, "core eudicots");
    assertParse(TaxGroup.Angiosperms, "Pentapetalae");
    // arthropods from COL, ITIS, iNaturalist and their sources
    assertParse(TaxGroup.Coleoptera, "Conotrachelini");
    assertParse(TaxGroup.Coleoptera, "Permocupedidae");
    assertParse(TaxGroup.Diptera, "Xanthochlorinae");
    assertParse(TaxGroup.OtherInsects, "Corduliinae");
    assertParse(TaxGroup.Arachnids, "Euophryini");
    assertParse(TaxGroup.Crustacean, "Canthocamptinae");
    assertParse(TaxGroup.OtherArthropods, "Limulidae");
    assertParse(TaxGroup.OtherArthropods, "Aphilodontidae");
    // homonyms in disparate groups are listed in their lowest common group
    assertParse(TaxGroup.Insects, "Tachinidae");
    assertParse(TaxGroup.Animals, "Cepheidae");
    assertParse(TaxGroup.Eukaryotes, "Carinae");
    // bacteria listed in the dicts win over suffix rules
    assertParse(TaxGroup.Bacteria, "Actinomycetes");
    assertParse(TaxGroup.Bacteria, "Chroococcophyceae");
    assertParse(TaxGroup.Bacteria, "Gloeobacterophycidae");
    assertParse(TaxGroup.Bacteria, "Nostocophycidae");
    assertParse(TaxGroup.Bacteria, "Oscillatoriophycidae");
    assertParse(TaxGroup.Bacteria, "Synechococcophycidae");
    assertParse(TaxGroup.Bacteria, "Myxophyceae");
    assertParse(TaxGroup.Bacteria, "Vampirovibrionophyceae");
    // cyanobacteria from LPSN, AlgaeBase and WoRMS
    assertParse(TaxGroup.Bacteria, "Cyanophyta");
    assertParse(TaxGroup.Bacteria, "Leptolyngbyales");
    assertParse(TaxGroup.Bacteria, "Oculatellaceae");
    assertParse(TaxGroup.Bacteria, "Spirulinoideae");
    assertParse(TaxGroup.Viruses, "Blablaviridae");
    // but not for binomials
    assertParse(null, "Blabla blamycetes");
    assertParse(null, "Blabla blaviridae");
    assertParse(null, "Blabla blaphytina");
    // zoological genus
    assertParse(null, "Neophyta");
    assertParse(null, "Protophyta");
  }

  @Test
  public void dictsExist() throws Exception {
    for (TaxGroup tg : TaxGroup.values()) {
      var res = getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt");
      assertNotNull(tg.name(), res);
    }
  }

  @Test
  public void dictsClash() throws Exception {
    Set<String> entries = new HashSet<>();
    for (TaxGroup tg : TaxGroup.values()) {
      var res = getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt");
      assertNotNull("missing parser file for " + tg, res);
      try (BufferedReader br = UTF8IoUtils.readerFromStream(res)) {
        br.lines().forEach( name -> {
          if (!StringUtils.isBlank(name)) {
            if (badNames.contains(name.toLowerCase().trim())) {
              throw new IllegalStateException(tg + ": bad name " + name);
            }
            assertTrue(tg + ": " + name, entries.add(name));
          }
        });
      }
      assertNotNull(tg.name(), res);
    }
  }

  /**
   * Ambiguous names are used in several disparate groups and are listed in the dictionary of their lowest common group only,
   * or in none if they have no common group.
   */
  @Test
  public void ambiguousInCommonGroup() throws Exception {
    Map<String, TaxGroup> listed = new HashMap<>();
    for (TaxGroup tg : TaxGroup.values()) {
      try (BufferedReader br = UTF8IoUtils.readerFromStream(getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt"))) {
        br.lines().map(l -> StringUtils.substringBefore(l, "#").trim()).filter(StringUtils::isNotBlank).forEach(n -> listed.put(n.toLowerCase(), tg));
      }
    }
    int counter = 0;
    try (BufferedReader br = UTF8IoUtils.readerFromStream(getClass().getResourceAsStream("/parser/dicts/taxgroup/ambiguous.txt"))) {
      for (String line : br.lines().toList()) {
        String name = StringUtils.substringBefore(line, "#").trim();
        Set<TaxGroup> groups = new HashSet<>();
        for (String g : StringUtils.substringAfter(line, "#").split(",")) {
          groups.add(TaxGroup.valueOf(g.trim()));
        }
        assertTrue(name + " needs at least 2 groups", groups.size() > 1);
        assertEquals(name, commonGroup(groups), listed.get(name.toLowerCase()));
        counter++;
      }
    }
    assertTrue(counter > 100);
  }

  /**
   * @return the lowest group containing all given groups or null if there is none
   */
  static TaxGroup commonGroup(Set<TaxGroup> groups) {
    Set<TaxGroup> common = null;
    for (TaxGroup g : groups) {
      Set<TaxGroup> cl = g.classification();
      cl.add(g);
      if (common == null) {
        common = cl;
      } else {
        common.retainAll(cl);
      }
    }
    final Set<TaxGroup> candidates = common;
    var lowest = candidates.stream().filter(c -> candidates.stream().noneMatch(o -> o != c && c.contains(o))).toList();
    return lowest.size() == 1 ? lowest.get(0) : null;
  }

  @Test
  @Override
  public void testUnparsable() throws Exception {
    // dont do anything
  }

}