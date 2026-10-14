package life.catalogue.resources.matching.gbif;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.UsageMatch;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class GbifMatchMapperTest {
  static final SimpleNameCached PLANTAE = cached("P", Rank.KINGDOM, "Plantae");
  static final SimpleNameCached PINACEAE = cached("F", Rank.FAMILY, "Pinaceae");
  static final SimpleNameCached ABIES = cached("G", Rank.GENUS, "Abies");

  static SimpleNameCached cached(String id, Rank rank, String name) {
    return new SimpleNameCached(id, name, rank);
  }

  static SimpleNameClassified<SimpleNameCached> usage(String id, Rank rank, TaxonomicStatus status, String name, String authorship,
                                                      SimpleNameCached... classification) {
    var u = SimpleNameClassified.snc(id, rank, NomCode.BOTANICAL, status, name, authorship);
    u.setClassification(List.of(classification));
    return u;
  }

  static List<String> keys(GbifNameUsageMatch m) {
    return m.classification.stream().map(rn -> rn.key).toList();
  }

  @Test
  public void accepted() {
    var alba = usage("S", Rank.SPECIES, TaxonomicStatus.ACCEPTED, "Abies alba", "Mill.", ABIES, PINACEAE, PLANTAE);
    var m = GbifMatchMapper.map(alba, UsageMatch.match(MatchType.EXACT, alba, 3, null), false, 7);

    assertEquals("S", m.usage.key);
    assertEquals("Abies alba Mill.", m.usage.name);
    assertEquals("Abies alba", m.usage.canonicalName);
    assertEquals("Mill.", m.usage.authorship);
    assertEquals("SPECIES", m.usage.rank);
    assertEquals("BOTANICAL", m.usage.code);
    assertEquals("ACCEPTED", m.usage.status);
    assertEquals("SCIENTIFIC", m.usage.type);
    assertFalse(m.synonym);
    assertNull(m.acceptedUsage);
    // root first, ending with the usage itself
    assertEquals(List.of("P", "F", "G", "S"), keys(m));
    assertEquals("KINGDOM", m.classification.getFirst().rank);
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals(100, (int) m.diagnostics.confidence);
    assertNull(m.diagnostics.note);
    assertEquals(7L, (long) m.diagnostics.timeTaken);
  }

  @Test
  public void synonym() {
    var alba = cached("S", Rank.SPECIES, "Abies alba");
    alba.setStatus(TaxonomicStatus.ACCEPTED);
    var syn = usage("SYN", Rank.SPECIES, TaxonomicStatus.SYNONYM, "Abies pectinata", "DC.", alba, ABIES, PINACEAE, PLANTAE);
    var m = GbifMatchMapper.map(syn, UsageMatch.match(MatchType.EXACT, syn, 3, null), false, 0);

    assertTrue(m.synonym);
    assertEquals("SYNONYM", m.usage.status);
    assertEquals("S", m.acceptedUsage.key);
    assertEquals("ACCEPTED", m.acceptedUsage.status);
    // the classification of a synonym ends with its accepted name
    assertEquals(List.of("P", "F", "G", "S"), keys(m));
  }

  @Test
  public void higherRank() {
    var abies = usage("G", Rank.GENUS, TaxonomicStatus.ACCEPTED, "Abies", "Mill.", PINACEAE, PLANTAE);
    var m = GbifMatchMapper.map(null, UsageMatch.match(MatchType.HIGHERRANK, abies, 3, null), false, 0);
    assertEquals("G", m.usage.key);
    assertEquals("HIGHERRANK", m.diagnostics.matchType);
    assertEquals(50, (int) m.diagnostics.confidence);
    assertNull(m.diagnostics.note);
  }

  @Test
  public void canonicalIsExact() {
    var alba = usage("S", Rank.SPECIES, TaxonomicStatus.ACCEPTED, "Abies alba", "Mill.", ABIES);
    var m = GbifMatchMapper.map(null, UsageMatch.match(MatchType.CANONICAL, alba, 3, null), false, 0);
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals(90, (int) m.diagnostics.confidence);
    assertEquals("clb matchType=CANONICAL", m.diagnostics.note);
  }

  @Test
  public void ambiguousPick() {
    var query = SimpleNameClassified.snc(null, Rank.GENUS, null, TaxonomicStatus.ACCEPTED, "Abies", "Mill.");
    var a1 = usage("G", Rank.GENUS, TaxonomicStatus.ACCEPTED, "Abies", "Mill.", PINACEAE);
    var a2 = usage("G2", Rank.GENUS, TaxonomicStatus.ACCEPTED, "Abies", "L.");
    var m = GbifMatchMapper.map(query, UsageMatch.match(MatchType.AMBIGUOUS, a1, 3, List.of(a1, a2)), true, 0);

    // the picked usage is kept, but with the lower ambiguous confidence
    assertEquals("G", m.usage.key);
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals(75, (int) m.diagnostics.confidence);
    assertEquals("clb matchType=AMBIGUOUS", m.diagnostics.note);
    assertEquals(1, m.diagnostics.alternatives.size());
    var alt = m.diagnostics.alternatives.getFirst();
    assertEquals("G2", alt.usage.key);
    assertEquals("VARIANT", alt.diagnostics.matchType);
    assertNull(alt.diagnostics.confidence);
  }

  @Test
  public void none() {
    var m = GbifMatchMapper.map(null, UsageMatch.empty(3), true, 0);
    assertNull(m.usage);
    assertNull(m.classification);
    assertFalse(m.synonym);
    assertEquals("NONE", m.diagnostics.matchType);
    assertEquals(100, (int) m.diagnostics.confidence);
    assertNull(m.diagnostics.alternatives);

    // an unresolved homonym has no usage, but is less confident than a plain no match
    var homonym = UsageMatch.unresolvedHomonym(MatchType.AMBIGUOUS, List.of(), 3);
    m = GbifMatchMapper.map(null, homonym, false, 0);
    assertNull(m.usage);
    assertEquals("NONE", m.diagnostics.matchType);
    assertEquals(75, (int) m.diagnostics.confidence);
    assertEquals("clb matchType=AMBIGUOUS", m.diagnostics.note);
  }
}
