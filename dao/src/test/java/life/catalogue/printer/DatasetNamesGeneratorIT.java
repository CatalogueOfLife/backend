package life.catalogue.printer;

import life.catalogue.dao.DaoTestBase;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.TestDataRule;
import life.catalogue.printer.diff.DiffNamesParam;
import life.catalogue.printer.diff.DiffOptions;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetNamesGeneratorIT extends DaoTestBase {

  public DatasetNamesGeneratorIT() {
    super(TestDataRule.tree());
  }

  // the dataset key staged by TestDataRule.tree(); reuse the same constant the diff ITs use
  private int datasetKey() {
    return TestDataRule.TREE.key;
  }

  private List<String> run(DiffNamesParam p) {
    List<String> out = new ArrayList<>();
    try (Cursor<String> c = mapper(NameUsageMapper.class).processDiffNames(p)) {
      c.forEach(out::add);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  @Test
  public void wholeDatasetSortedByteOrder() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(true);
    p.setAuthorship(true);
    List<String> names = run(p);
    assertFalse(names.isEmpty());
    for (int i = 1; i < names.size(); i++) {
      assertTrue("not byte-sorted at " + i + ": " + names.get(i - 1) + " vs " + names.get(i),
        DiffOptions.CODEPOINT.compare(names.get(i - 1), names.get(i)) <= 0);
    }
  }

  @Test
  public void authorshipOffOmitsAuthors() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(false);
    p.setAuthorship(false);
    List<String> withAuth = run(new DiffNamesParam() {{ setDatasetKey(datasetKey()); setSynonyms(false); setAuthorship(true); }});
    List<String> noAuth = run(p);
    assertFalse(noAuth.isEmpty());
    // authorship-off labels are never longer than authorship-on for the same set
    assertTrue(String.join("", noAuth).length() <= String.join("", withAuth).length());
  }

  /**
   * Ancestor-at-rank suffix: with parentRank=GENUS every species/subspecies label carries
   * " >> <its genus>", while the genus itself and higher ranks carry no suffix (a name is never
   * its own ancestor). Dataset 11 genera are Lynx, Canis, Urocyon.
   */
  @Test
  public void ancestorAtGenusRankSuffix() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(false);      // accepted-only for a deterministic tree
    p.setAuthorship(false);    // no authorship column in the test data anyway
    p.setParentName(true);
    p.setParentRank(Rank.GENUS);
    List<String> names = run(p);

    // species and subspecies get the genus ancestor appended
    assertTrue(names.toString(), names.contains("Lynx lynx >> Lynx"));
    assertTrue(names.toString(), names.contains("Lynx rufus >> Lynx"));
    assertTrue(names.toString(), names.contains("Lynx rufus baileyi >> Lynx"));   // subspecies -> genus (skips species)
    assertTrue(names.toString(), names.contains("Lynx rufus gigas >> Lynx"));
    assertTrue(names.toString(), names.contains("Canis adustus >> Canis"));
    assertTrue(names.toString(), names.contains("Canis argentinus >> Canis"));
    assertTrue(names.toString(), names.contains("Canis aureus >> Canis"));
    assertTrue(names.toString(), names.contains("Urocyon citrinus >> Urocyon"));
    assertTrue(names.toString(), names.contains("Urocyon webbi >> Urocyon"));

    // the genus itself and higher ranks have no ancestor at GENUS -> bare name, no suffix
    assertTrue(names.toString(), names.contains("Lynx"));
    assertTrue(names.toString(), names.contains("Canis"));
    assertTrue(names.toString(), names.contains("Urocyon"));
    assertTrue(names.toString(), names.contains("Felidae"));
    assertTrue(names.toString(), names.contains("Animalia"));
    // and no genus/higher label accidentally acquired a suffix
    assertFalse(names.toString(), names.contains("Lynx >> Lynx"));
    for (String label : names) {
      if (label.contains(" >> ")) {
        String anc = label.substring(label.indexOf(" >> ") + 4);
        assertTrue("suffix must be a genus, was: " + label, anc.equals("Lynx") || anc.equals("Canis") || anc.equals("Urocyon"));
      }
    }
  }

  /**
   * Direct-parent suffix: with parentRank=null the " >> " suffix is always the direct parent name,
   * for every rank (order->class, genus->family, subspecies->species, root->none).
   */
  @Test
  public void directParentSuffix() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(false);
    p.setAuthorship(false);
    p.setParentName(true);
    p.setParentRank(null);     // direct parent
    List<String> names = run(p);

    assertTrue(names.toString(), names.contains("Lynx rufus baileyi >> Lynx rufus")); // subspecies -> its species parent
    assertTrue(names.toString(), names.contains("Lynx lynx >> Lynx"));                 // species -> its genus parent
    assertTrue(names.toString(), names.contains("Lynx >> Felidae"));                   // genus -> its family parent
    assertTrue(names.toString(), names.contains("Urocyon >> Carnivora"));              // genus -> order parent (no family in this branch)
    assertTrue(names.toString(), names.contains("Chordata >> Animalia"));              // phylum -> kingdom parent
    // the root taxon has no parent -> bare name, no suffix
    assertTrue(names.toString(), names.contains("Animalia"));
    assertFalse(names.toString(), names.stream().anyMatch(l -> l.startsWith("Animalia >>")));
  }
}
