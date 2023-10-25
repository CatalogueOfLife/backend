package life.catalogue.assembly;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.MatchedParentStack;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParentStackTest {

  @Test
  public void testStack() throws Exception {
    SimpleNameWithNidx king = new SimpleNameWithNidx();
    king.setName("MasterTax");
    MatchedParentStack parents = new MatchedParentStack(king);

    assertEquals(0, parents.size());
    assertNull(parents.last());
    assertEquals(king, parents.lowestParentMatch());

    parents.push(src(1, null));
    parents.push(src(2, 1));
    var nub = match("nub#3");
    parents.setMatch(nub);
    assertEquals(nub, parents.lowestParentMatch());
    assertEquals(2, parents.size());

    assertFalse(parents.isDoubtful());
    parents.markSubtreeAsDoubtful(); // doubtful key=2
    assertTrue(parents.isDoubtful());

    parents.push(src(3, 2));
    assertEquals(3, parents.size());
    assertEquals(nub, parents.lowestParentMatch());
    assertTrue(parents.isDoubtful());

    parents.push(src(4, 1)); // this removes all but the first key
    assertEquals(2, parents.size());
    assertFalse(parents.isDoubtful());
    assertNotNull(parents.last());
  }

  @Test
  public void testInvalidRankOrder() throws Exception {
    SimpleNameWithNidx biota = new SimpleNameWithNidx();
    biota.setId("0");
    biota.setName("Biota");
    biota.setRank(Rank.UNRANKED);
    MatchedParentStack parents = new MatchedParentStack(biota);

    assertEquals(0, parents.size());
    assertNull(parents.last());
    assertEquals(biota, parents.lowestParentMatch());
    assertFalse(parents.isDoubtful());

    parents.push(src(Rank.KINGDOM, 1,0));
    parents.push(src(Rank.PHYLUM, 2,1));
    assertEquals(2, parents.size());
    assertEquals("2", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    parents.push(src(Rank.SUPERPHYLUM, 3,2));
    assertEquals(3, parents.size());
    assertEquals("3", parents.last().usage.getId());
    assertTrue(parents.isDoubtful());

    parents.push(src(Rank.SUPERPHYLUM, 4,1));
    assertEquals(2, parents.size());
    assertEquals("4", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    // ambiguous ranks, botany
    parents.push(src(Rank.GENUS, 5,4));
    parents.push(src(Rank.SECTION_BOTANY, 6,5));
    assertEquals(4, parents.size());
    assertEquals("6", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    parents.push(src(Rank.GENUS, 5,4));
    parents.push(src(Rank.SECTION_BOTANY, 6,5));
    assertEquals(4, parents.size());
    assertEquals("6", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    parents.push(src(Rank.SERIES, 7,6));
    assertEquals(5, parents.size());
    assertEquals("7", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    // zoological way
    parents.push(src(Rank.ORDER, 8,4));
    parents.push(src(Rank.SECTION_ZOOLOGY, 9,8));
    parents.push(src(Rank.FAMILY, 10,9));
    assertEquals(5, parents.size());
    assertEquals("10", parents.last().usage.getId());
    assertFalse(parents.isDoubtful());

    parents.push(src(Rank.SUPERFAMILY, 11,10));
    assertEquals(6, parents.size());
    assertEquals("11", parents.last().usage.getId());
    assertTrue(parents.isDoubtful());
  }

  private SimpleNameWithNidx src(Rank rank, int key, Integer parentKey) {
    var sn = src(key, parentKey);
    sn.setRank(rank);
    return sn;
  }

  private SimpleNameWithNidx src(int key, Integer parentKey) {
    SimpleNameWithNidx u = new SimpleNameWithNidx();
    u.setId(String.valueOf(key));
    u.setParent(parentKey == null ? null : String.valueOf(parentKey));
    u.setName("Sciname #" + key);
    u.setStatus(TaxonomicStatus.ACCEPTED);
    return u;
  }

  private SimpleNameWithNidx match(String name) {
    SimpleNameWithNidx n = new SimpleNameWithNidx();
    n.setName(name);
    return n;
  }

}