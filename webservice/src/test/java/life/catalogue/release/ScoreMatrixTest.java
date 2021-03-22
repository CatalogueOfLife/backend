package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ScoreMatrixTest {

  static ScoreMatrix.ReleaseMatch m(int attempt, int id, TaxonomicStatus status, String authorship) {
    SimpleNameWithNidx sn = new SimpleNameWithNidx();
    sn.setCanonicalId(1);
    sn.setNamesIndexId(1);
    sn.setNamesIndexMatchType(MatchType.EXACT);
    sn.setStatus(status);
    sn.setName("Abies alba");
    sn.setAuthorship(authorship);
    ReleasedIds.ReleasedId rid = new ReleasedIds.ReleasedId(id,attempt,sn);
    ScoreMatrix.ReleaseMatch m = new ScoreMatrix.ReleaseMatch(1,1,1, sn, rid);
    return m;
  }
  @Test
  public void sortReleaseMatch() {
    var m1 = m(1,14, TaxonomicStatus.ACCEPTED, null);
    var m2 = m(2,12, TaxonomicStatus.ACCEPTED, "Mill.");
    var m3 = m(2,12, TaxonomicStatus.SYNONYM, "Mill.");
    var m4 = m(1,12, TaxonomicStatus.ACCEPTED, "Mill.");
    var m5 = m(3,22, TaxonomicStatus.ACCEPTED, "Mill.");
    var m6 = m(1,7, TaxonomicStatus.ACCEPTED, null);

    List<ScoreMatrix.ReleaseMatch> sorted = new ArrayList<>(List.of(m1,m2,m3,m4,m5,m6));
    Collections.sort(sorted);

    assertEquals(m6, sorted.get(0));
    assertEquals(m4, sorted.get(1));
    assertEquals(m2, sorted.get(2));
    assertEquals(m3, sorted.get(3));
    assertEquals(m1, sorted.get(4));
    assertEquals(m5, sorted.get(5));
  }

  @Test
  public void testMatrix() {
    List<SimpleNameWithNidx> names = List.of(
      sn(1), sn(2), sn(3), sn(4), sn(5), sn(6), sn(7), sn(8)
    );
    ReleasedIds.ReleasedId[] rids = new ReleasedIds.ReleasedId[5];
    rids[0] = rid(1,2);
    rids[1] = rid(2,2);
    rids[2] = rid(3,2);
    rids[3] = rid(4,1);
    rids[4] = rid(5,1);
    var sm = new ScoreMatrix(names, rids, (sn, rid) -> (Integer.parseInt(sn.getId())-rid.id) / rid.attempt);
    sm.printMatrix();

    int score = 4;
    var m = sm.highest();
    while(!m.isEmpty()) {
      sm.printMatrix();
      assertFalse(m.isEmpty());
      assertEquals(score, m.get(0).score);
      sm.remove(m.get(0));
      score--;
      m = sm.highest();
    }
    assertEquals(0, score);
  }

  @Test
  public void test2x2Matrix() {
    List<SimpleNameWithNidx> names = List.of(
      sn(1), sn(2)
    );
    ReleasedIds.ReleasedId[] rids = new ReleasedIds.ReleasedId[2];
    rids[0] = rid(1,2);
    rids[1] = rid(2,2);
    AtomicInteger sc = new AtomicInteger(10);
    var sm = new ScoreMatrix(names, rids, (sn, rid) -> {
      if (Integer.parseInt(sn.getId()) == rid.id) {
        return 0;
      }
      return sc.getAndIncrement();
    });
    sm.printMatrix();

    var matches = sm.highest();
    assertEquals(1, matches.size());
    var m = matches.get(0);
    assertEquals(11, m.score);
    assertEquals(1, m.rid.id);
    assertEquals("2", m.name.getId());
    sm.remove(m);
    sm.printMatrix();

    matches = sm.highest();
    assertEquals(1, matches.size());
    m = matches.get(0);
    assertEquals(10, m.score);
    assertEquals(2, m.rid.id);
    assertEquals("1", m.name.getId());
    sm.remove(m);
    sm.printMatrix();

    matches = sm.highest();
    assertTrue(matches.isEmpty());
  }

  static SimpleNameWithNidx sn(int id) {
    SimpleNameWithNidx sn = new SimpleNameWithNidx();
    sn.setId(String.valueOf(id));
    return sn;
  }

  static ReleasedIds.ReleasedId rid(int id, int attempt) {
    var m = m(attempt, id, TaxonomicStatus.ACCEPTED, null);
    return m.rid;
  }
}