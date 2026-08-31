package life.catalogue.importer.store;

import life.catalogue.db.mapper.NameUsageMapper.GeneratedUsage;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviousIdsTest {

  static GeneratedUsage u(String usageId, String nameId, Rank rank, String name, String authorship, String parent) {
    var g = new GeneratedUsage();
    g.usageId = usageId;
    g.nameId = nameId;
    g.rank = rank;
    g.scientificName = name;
    g.authorship = authorship;
    g.parent = parent;
    return g;
  }

  static final java.util.function.Predicate<PreviousIds.IdPair> FREE = p -> true;

  @Test
  public void keyIsNormalised() {
    String k = PreviousIds.key(Rank.GENUS, "Aster", "L.");
    assertEquals(k, PreviousIds.key(Rank.GENUS, " aster ", "L"));
    assertEquals(k, PreviousIds.key(Rank.GENUS, "ASTER", "l."));
    // diacritics fold to ascii
    assertEquals(PreviousIds.key(Rank.SPECIES, "Abies alba", "Müller"),
                 PreviousIds.key(Rank.SPECIES, "Abies alba", "Muller"));
    // a missing rank is not the same as a different one
    assertEquals(PreviousIds.key(Rank.UNRANKED, "Aster", null), PreviousIds.key(null, "Aster", null));
    assertNotEquals(k, PreviousIds.key(Rank.FAMILY, "Aster", "L."));
    assertNotEquals(k, PreviousIds.key(Rank.GENUS, "Aster", null));
  }

  @Test
  public void noKeyWithoutAName() {
    assertNull(PreviousIds.key(Rank.GENUS, null, "L."));
    assertNull(PreviousIds.key(Rank.GENUS, "  ", "L."));
  }

  @Test
  public void none() {
    assertNull(PreviousIds.NONE.take(Rank.GENUS, "Aster", null, null, FREE));
    assertNull(PreviousIds.NONE.nameIdFor("x1"));
    assertFalse(PreviousIds.NONE.isReserved("x1"));
    assertEquals(0, PreviousIds.NONE.size());
  }

  @Test
  public void handedOutOnlyOnce() {
    var ids = PreviousIds.of(false, u("x1", "x2", Rank.GENUS, "Aster", null, "Asteraceae"));

    var p = ids.take(Rank.GENUS, "Aster", null, "Asteraceae", FREE);
    assertNotNull(p);
    assertEquals("x1", p.usageId());
    assertEquals("x2", p.nameId());
    assertEquals(1, ids.getReused());

    assertNull(ids.take(Rank.GENUS, "Aster", null, "Asteraceae", FREE));
    assertEquals(1, ids.getMissed());
  }

  @Test
  public void duplicatesComeOutInIdOrder() {
    // deliberately loaded in reverse, the order must not depend on it
    var ids = PreviousIds.of(false,
      u("x9", "xA", Rank.GENUS, "Aster", null, null),
      u("x3", "x4", Rank.GENUS, "Aster", null, null)
    );
    assertEquals("x3", ids.take(Rank.GENUS, "Aster", null, null, FREE).usageId());
    assertEquals("x9", ids.take(Rank.GENUS, "Aster", null, null, FREE).usageId());
    assertNull(ids.take(Rank.GENUS, "Aster", null, null, FREE));
  }

  @Test
  public void parentTellsHomonymsApart() {
    var ids = PreviousIds.of(false,
      u("x3", "x4", Rank.GENUS, "Aster", null, "Asteraceae"),
      u("x9", "xA", Rank.GENUS, "Aster", null, "Poaceae")
    );
    // the lower id would win without the parent
    assertEquals("x9", ids.take(Rank.GENUS, "Aster", null, "poaceae", FREE).usageId());
    assertEquals("x3", ids.take(Rank.GENUS, "Aster", null, "Asteraceae", FREE).usageId());
  }

  @Test
  public void unknownParentFallsBackToTheFirst() {
    var ids = PreviousIds.of(false,
      u("x3", "x4", Rank.GENUS, "Aster", null, "Asteraceae"),
      u("x9", "xA", Rank.GENUS, "Aster", null, "Poaceae")
    );
    assertEquals("x3", ids.take(Rank.GENUS, "Aster", null, "Rosaceae", FREE).usageId());
    assertEquals("x9", ids.take(Rank.GENUS, "Aster", null, null, FREE).usageId());
  }

  @Test
  public void takenCandidatesAreSkipped() {
    var ids = PreviousIds.of(false,
      u("x3", "x4", Rank.GENUS, "Aster", null, null),
      u("x9", "xA", Rank.GENUS, "Aster", null, null)
    );
    var p = ids.take(Rank.GENUS, "Aster", null, null, pair -> !pair.usageId().equals("x3"));
    assertEquals("x9", p.usageId());
    assertEquals(1, ids.getBlocked());
    assertEquals(1, ids.getReused());
  }

  @Test
  public void everyIdIsReservedEvenWithoutAKey() {
    var ids = PreviousIds.of(false,
      u("x1", "x2", Rank.GENUS, "Aster", null, null),
      u("x5", "x6", Rank.GENUS, null, null, null) // no name, so no candidate
    );
    assertTrue(ids.isReserved("x1"));
    assertTrue(ids.isReserved("x2"));
    assertTrue(ids.isReserved("x5"));
    assertTrue(ids.isReserved("x6"));
    assertFalse(ids.isReserved("x7"));
    assertFalse(ids.isReserved(null));
    assertEquals(4, ids.size());
  }

  @Test
  public void nameIdByUsageIdOnlyForSources() {
    var row = u("3", "3", Rank.SPECIES, "Aster alpinus", "L.", "Aster");
    assertEquals("3", PreviousIds.of(true, row).nameIdFor("3"));
    assertNull(PreviousIds.of(false, row).nameIdFor("3"));
  }
}
