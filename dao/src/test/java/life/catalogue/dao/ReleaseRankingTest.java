package life.catalogue.dao;

import life.catalogue.db.mapper.DatasetMapper.ArchivableRelease;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import static life.catalogue.api.vocab.DatasetOrigin.RELEASE;
import static life.catalogue.api.vocab.DatasetOrigin.XRELEASE;
import static org.junit.Assert.*;

public class ReleaseRankingTest {
  static final LocalDateTime JAN = LocalDateTime.of(2026, 1, 1, 10, 0);

  static ArchivableRelease base(int key, int attempt, LocalDateTime created) {
    return new ArchivableRelease(key, RELEASE, attempt, false, created, null, null);
  }

  static ArchivableRelease xr(int key, int attempt, LocalDateTime created, Integer baseKey) {
    return new ArchivableRelease(key, XRELEASE, attempt, false, created, null, baseKey);
  }

  static ReleaseRanking rank(IntSet ignored, ArchivableRelease... releases) {
    return new ReleaseRanking(3, List.of(releases), ignored);
  }

  static ReleaseRanking rank(ArchivableRelease... releases) {
    return rank(new IntOpenHashSet(), releases);
  }

  static List<Integer> keys(List<ArchivableRelease> releases) {
    return releases.stream().map(ArchivableRelease::getKey).toList();
  }

  @Test
  public void generationsNewestFirstWithBaseAboveItsExtendedReleases() {
    var r = rank(
      base(10, 1, JAN),
      xr(11, 2, JAN.plusDays(3), 10),
      base(20, 3, JAN.plusMonths(1)),
      xr(21, 4, JAN.plusMonths(1).plusDays(3), 20),
      xr(22, 5, JAN.plusMonths(1).plusDays(9), 20)
    );
    assertEquals(List.of(20, 22, 21, 10, 11), keys(r.archivable()));
    assertTrue(r.isTop(20));
    assertFalse(r.isTop(22));
  }

  @Test
  public void lateExtendedReleaseStaysInItsGeneration() {
    // the extended release of 10 was built after base release 20 existed
    var r = rank(
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      xr(11, 3, JAN.plusMonths(1).plusDays(5), 10)
    );
    assertEquals(List.of(20, 10, 11), keys(r.archivable()));
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertFalse(r.isFallbackBase(11));
  }

  @Test
  public void fallbackBaseIsTheNewestPublicBaseReleaseAtLeastOneDayOlder() {
    var created = JAN.plusMonths(2);
    var r = rank(
      base(10, 1, JAN),
      new ArchivableRelease(20, RELEASE, 2, true, JAN.plusMonths(1), null, null), // private
      new ArchivableRelease(30, RELEASE, 3, false, JAN.plusMonths(1).plusDays(5), created.minusDays(2), null), // deleted before the XR
      base(40, 4, created.minusHours(12)), // less than a full day older
      xr(50, 5, created, null)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(50));
    assertTrue(r.isFallbackBase(50));
  }

  @Test
  public void fallbackBaseMayHaveBeenDeletedSince() {
    var created = JAN.plusMonths(1);
    var r = rank(
      new ArchivableRelease(10, RELEASE, 1, false, JAN, created.plusMonths(12), null),
      xr(11, 2, created, null)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertFalse(r.isArchivable(10));
  }

  @Test
  public void unknownRecordedBaseFallsBack() {
    var r = rank(
      base(10, 1, JAN),
      xr(11, 2, JAN.plusDays(3), 999)
    );
    assertEquals(Integer.valueOf(10), r.baseRelease(11));
    assertTrue(r.isFallbackBase(11));
  }

  @Test
  public void privateAndDeletedReleasesAreNotArchivable() {
    var r = rank(
      base(10, 1, JAN),
      new ArchivableRelease(20, RELEASE, 2, true, JAN.plusMonths(1), null, null),
      new ArchivableRelease(30, RELEASE, 3, false, JAN.plusMonths(2), JAN.plusMonths(3), null)
    );
    assertEquals(List.of(10), keys(r.archivable()));
    assertTrue(r.isTop(10));
    assertFalse(r.supplies(20));
    assertFalse(r.supplies(30));
  }

  @Test
  public void ignoredReleasesAreArchivableButSupplyNothing() {
    var r = rank(new IntOpenHashSet(new int[]{20}),
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      base(30, 3, JAN.plusMonths(2))
    );
    assertEquals(List.of(30, 20, 10), keys(r.archivable()));
    assertTrue(r.isArchivable(20));
    assertFalse(r.supplies(20));
    assertTrue(r.supplies(10));
  }

  @Test
  public void blockingKeys() {
    var r = rank(new IntOpenHashSet(new int[]{20}),
      base(10, 1, JAN),
      base(20, 2, JAN.plusMonths(1)),
      base(30, 3, JAN.plusMonths(2))
    );
    // a supplying release is blocked by the supplying releases ranked above it
    assertEquals(List.of(), r.blockingKeys(30));
    assertEquals(List.of(30), r.blockingKeys(10));
    // a release supplying nothing never overwrites a version any supplying release holds
    assertEquals(List.of(30, 10), r.blockingKeys(20));
  }

  @Test(expected = IllegalArgumentException.class)
  public void unknownRelease() {
    rank(base(10, 1, JAN)).supplies(99);
  }
}
