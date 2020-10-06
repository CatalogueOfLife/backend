package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ScoreMatrixTest {

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

  static SimpleNameWithNidx sn(int id) {
    SimpleNameWithNidx sn = new SimpleNameWithNidx();
    sn.setId(String.valueOf(id));
    return sn;
  }

  static ReleasedIds.ReleasedId rid(int id, int attempt) {
    return new ReleasedIds.ReleasedId(id, 1, attempt);
  }
}