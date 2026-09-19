package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static life.catalogue.matching.UsageMatcher.Lineage;
import static life.catalogue.matching.UsageMatcher.compareLineage;
import static life.catalogue.matching.UsageMatcher.isEvidenceRank;
import static org.junit.Assert.*;

/**
 * Unit tests for the lineage comparison that decides whether two same-named, differently authored genera
 * are the same taxon. See txtree/genushomonyms/readme.md and data#1718 for the end to end scenario.
 */
public class UsageMatcherTest {

  static SimpleNameCached sn(Rank rank, String name) {
    return new SimpleNameCached(name, name, rank);
  }

  static SimpleNameClassified<SimpleNameCached> candidate(SimpleNameCached... classification) {
    var snc = new SimpleNameClassified<SimpleNameCached>();
    snc.setClassification(List.of(classification));
    return snc;
  }

  @Test
  public void evidenceRankWindow() {
    // the window is FAMILY up to ORDER, inclusive on both ends
    assertTrue(isEvidenceRank(Rank.FAMILY));
    assertTrue(isEvidenceRank(Rank.SUPERFAMILY));
    assertTrue(isEvidenceRank(Rank.ORDER));

    // too high to carry weight - every beetle genus shares a kingdom with every other one
    assertFalse(isEvidenceRank(Rank.CLASS));
    assertFalse(isEvidenceRank(Rank.PHYLUM));
    assertFalse(isEvidenceRank(Rank.KINGDOM));

    // below family the ranks are too unstable across sources to decide on
    assertFalse(isEvidenceRank(Rank.SUBFAMILY));
    assertFalse(isEvidenceRank(Rank.TRIBE));
    assertFalse(isEvidenceRank(Rank.GENUS));

    // and nothing we cannot order
    assertFalse(isEvidenceRank(null));
    assertFalse(isEvidenceRank(Rank.UNRANKED));
    assertFalse(isEvidenceRank(Rank.OTHER));
    assertFalse(isEvidenceRank(Rank.SUPRAGENERIC_NAME));
  }

  @Test
  public void sameWhenTheyAgreeAtTheLowestSharedRank() {
    // the Amanita case: the source has no family at all, so the lowest shared rank is the order
    var candidate = candidate(
      sn(Rank.FAMILY, "Amanitaceae"), sn(Rank.ORDER, "Agaricales"),
      sn(Rank.CLASS, "Agaricomycetes"), sn(Rank.PHYLUM, "Basidiomycota"), sn(Rank.KINGDOM, "Fungi")
    );
    var parents = List.of(sn(Rank.KINGDOM, "Fungi"), sn(Rank.PHYLUM, "Basidiomycota"), sn(Rank.ORDER, "Agaricales"));
    assertEquals(Lineage.SAME, compareLineage(candidate, parents));
  }

  @Test
  public void conflictWhenTheLowestSharedRankDisagrees() {
    // both carry a family and the families differ - real homonyms
    var candidate = candidate(
      sn(Rank.FAMILY, "Staphylinidae"), sn(Rank.ORDER, "Coleoptera"), sn(Rank.CLASS, "Insecta")
    );
    var parents = List.of(sn(Rank.CLASS, "Insecta"), sn(Rank.ORDER, "Coleoptera"), sn(Rank.FAMILY, "Tenebrionidae"));
    assertEquals(Lineage.CONFLICT, compareLineage(candidate, parents));
  }

  @Test
  public void lowestSharedRankWinsOverAHigherAgreeingOne() {
    // the two agree at ORDER but conflict at FAMILY. The family is the lowest rank they share, so it
    // decides - if the agreeing order could stand in for it, two real homonyms would be merged.
    var candidate = candidate(sn(Rank.FAMILY, "Staphylinidae"), sn(Rank.ORDER, "Coleoptera"));
    var parents = List.of(sn(Rank.ORDER, "Coleoptera"), sn(Rank.FAMILY, "Tenebrionidae"));
    assertEquals(Lineage.CONFLICT, compareLineage(candidate, parents));
  }

  @Test
  public void undecidedWhenNothingIsSharedInTheWindow() {
    // the source places its genus straight under the phylum, so nothing between family and order is shared
    var candidate = candidate(
      sn(Rank.FAMILY, "Tenebrionidae"), sn(Rank.ORDER, "Coleoptera"),
      sn(Rank.CLASS, "Insecta"), sn(Rank.PHYLUM, "Arthropoda"), sn(Rank.KINGDOM, "Animalia")
    );
    var parents = List.of(sn(Rank.KINGDOM, "Animalia"), sn(Rank.PHYLUM, "Arthropoda"));
    assertEquals(Lineage.UNDECIDED, compareLineage(candidate, parents));
  }

  @Test
  public void undecidedWithoutAnyClassification() {
    var candidate = candidate(sn(Rank.FAMILY, "Tenebrionidae"));
    assertEquals(Lineage.UNDECIDED, compareLineage(candidate, List.of()));
    assertEquals(Lineage.UNDECIDED, compareLineage(candidate, null));
  }

  @Test
  public void namesCompareIgnoringCase() {
    // some sources shout their higher taxa, e.g. txtree/author-dupes/iucn.txtree
    var candidate = candidate(sn(Rank.FAMILY, "Tenebrionidae"), sn(Rank.ORDER, "Coleoptera"));
    var parents = List.of(sn(Rank.ORDER, "COLEOPTERA"), sn(Rank.FAMILY, "TENEBRIONIDAE"));
    assertEquals(Lineage.SAME, compareLineage(candidate, parents));
  }
}
