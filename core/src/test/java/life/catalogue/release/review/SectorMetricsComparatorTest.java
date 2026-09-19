package life.catalogue.release.review;

import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorMetrics;
import life.catalogue.api.model.SectorMetricsDiff;
import life.catalogue.api.model.SectorMetricsDiff.Flag;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The flag classification, which is the half of the sector comparison a release review actually reads.
 * No database - it is a pure join over two lists.
 */
public class SectorMetricsComparatorTest {
  static final double MIN = 0.1;

  static SectorMetrics sm(int key, Integer attempt, int usages) {
    var m = new SectorMetrics();
    m.setSectorKey(key);
    m.setMode(Sector.Mode.ATTACH);
    m.setSubjectDatasetKey(1000 + key);
    m.setSubjectName("Subject" + key);
    m.setTargetName("Target" + key);
    m.setAttempt(attempt);
    m.setUsagesCount(usages);
    m.setTaxonCount(usages);
    m.setSynonymCount(0);
    return m;
  }

  static SectorMetricsDiff one(SectorMetrics cur, SectorMetrics prev) {
    return SectorMetricsComparator.diff(cur, prev, MIN);
  }

  @Test
  public void unchangedIsNotReported() {
    assertNull(one(sm(1, 9, 1000), sm(1, 8, 1000)));
    // below the threshold in both directions
    assertNull(one(sm(1, 9, 1050), sm(1, 8, 1000)));
    assertNull(one(sm(1, 9, 950), sm(1, 8, 1000)));
    // a sector that was empty and still is says nothing about this release
    assertNull(one(sm(1, 9, 0), sm(1, 8, 0)));
    assertNull(one(null, null));
  }

  @Test
  public void zeroBeatsEverythingElse() {
    var d = one(sm(193, 93, 0), sm(193, 91, 1042));
    assertNotNull(d);
    assertEquals(Flag.ZERO, d.getFlag());
    assertEquals(193, d.getSectorKey());
    assertEquals(0, d.getUsagesCount());
    assertEquals(1042, d.getPrevUsagesCount());
    assertEquals((Integer) 93, d.getAttempt());
    assertEquals((Integer) 91, d.getPrevAttempt());
    assertEquals(-1.0, d.getChange(), 0.0000001);
    // the descriptive columns come from the current sector
    assertEquals(Sector.Mode.ATTACH, d.getMode());
    assertEquals("Subject193", d.getSubjectName());
    assertEquals("Target193", d.getTargetName());
  }

  @Test
  public void increasedAndDecreased() {
    var up = one(sm(1, 9, 1500), sm(1, 8, 1000));
    assertEquals(Flag.INCREASED, up.getFlag());
    assertEquals(0.5, up.getChange(), 0.0000001);

    var down = one(sm(1, 9, 500), sm(1, 8, 1000));
    assertEquals(Flag.DECREASED, down.getFlag());
    assertEquals(-0.5, down.getChange(), 0.0000001);
  }

  /**
   * Growth out of nothing has no percentage to report, but is still growth and must not be swallowed.
   */
  @Test
  public void growthFromZeroHasNoPercentage() {
    var d = one(sm(1, 9, 320), sm(1, 8, 0));
    assertEquals(Flag.INCREASED, d.getFlag());
    assertNull(d.getChange());
  }

  @Test
  public void newAndRemoved() {
    var added = one(sm(7, 3, 55), null);
    assertEquals(Flag.NEW, added.getFlag());
    assertEquals(55, added.getUsagesCount());
    assertEquals(0, added.getPrevUsagesCount());
    assertNull(added.getPrevAttempt());

    var gone = one(null, sm(7, 3, 55));
    assertEquals(Flag.REMOVED, gone.getFlag());
    assertEquals(0, gone.getUsagesCount());
    assertEquals(55, gone.getPrevUsagesCount());
    // the descriptive columns fall back to the sector that is gone, or the row would be unreadable
    assertEquals("Subject7", gone.getSubjectName());
    assertNull(gone.getAttempt());
    assertEquals((Integer) 3, gone.getPrevAttempt());
  }

  /**
   * A brand new sector that contributes nothing is still news - it means a sector was added and its sync
   * produced nothing at all.
   */
  @Test
  public void newAndEmpty() {
    var d = one(sm(7, 3, 0), null);
    assertEquals(Flag.NEW, d.getFlag());
    assertEquals(0, d.getUsagesCount());
  }

  @Test
  public void minChangeIsHonoured() {
    // +5%
    assertNull(SectorMetricsComparator.diff(sm(1, 9, 1050), sm(1, 8, 1000), 0.1));
    assertEquals(Flag.INCREASED, SectorMetricsComparator.diff(sm(1, 9, 1050), sm(1, 8, 1000), 0.01).getFlag());
    // a threshold of 0 reports anything that moved at all, but still not a sector that did not move
    assertNull(SectorMetricsComparator.diff(sm(1, 9, 1000), sm(1, 8, 1000), 0));
    assertNotNull(SectorMetricsComparator.diff(sm(1, 9, 1001), sm(1, 8, 1000), 0));
  }

  @Test
  public void joinsBySectorIdAndSortsWorstFirst() {
    var current = List.of(
      sm(1, 9, 1000),   // unchanged, dropped
      sm(2, 9, 0),      // lost everything
      sm(3, 9, 5000),   // tripled
      sm(4, 9, 10)      // new
    );
    var previous = List.of(
      sm(1, 8, 1000),
      sm(2, 8, 800),
      sm(3, 8, 1500),
      sm(9, 8, 700)     // gone
    );
    var diffs = SectorMetricsComparator.compare(current, previous, MIN);
    var keys = diffs.stream().map(SectorMetricsDiff::getSectorKey).collect(Collectors.toList());
    assertEquals(List.of(2, 3, 4, 9), keys);
    assertEquals(Flag.ZERO, diffs.get(0).getFlag());
    assertEquals(Flag.INCREASED, diffs.get(1).getFlag());
    assertEquals(Flag.NEW, diffs.get(2).getFlag());
    assertEquals(Flag.REMOVED, diffs.get(3).getFlag());
    // the unchanged sector is simply absent
    assertTrue(diffs.stream().noneMatch(d -> d.getSectorKey() == 1));
  }

  @Test
  public void nullAndEmptyInputs() {
    assertTrue(SectorMetricsComparator.compare(null, null, MIN).isEmpty());
    assertTrue(SectorMetricsComparator.compare(List.of(), List.of(), MIN).isEmpty());
    // everything is new when there is nothing to compare against
    var diffs = SectorMetricsComparator.compare(List.of(sm(1, 9, 5), sm(2, 9, 6)), List.of(), MIN);
    assertEquals(2, diffs.size());
    assertTrue(diffs.stream().allMatch(d -> d.getFlag() == Flag.NEW));
  }

  @Test
  public void relativeChange() {
    assertEquals(0d, SectorMetricsComparator.relativeChange(0, 0), 0.0000001);
    assertNull(SectorMetricsComparator.relativeChange(10, 0));
    assertEquals(-1d, SectorMetricsComparator.relativeChange(0, 10), 0.0000001);
    assertEquals(1d, SectorMetricsComparator.relativeChange(20, 10), 0.0000001);
  }
}
