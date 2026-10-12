package life.catalogue.command;

import life.catalogue.dao.Partitioner;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RepartitionCmdIT extends CmdTestBase {

  public RepartitionCmdIT() {
    super(RepartitionCmd::new, TestDataRule.apple());
  }

  @Test
  public void testRebuildAll() throws Exception {
    final Map<String, Integer> before = counts();
    assertTrue(before.get("name_usage") > 0);

    assertTrue(run("repartition", "--prompt", "0", "--num", "3").isEmpty());

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertEquals(3, Partitioner.detectPartitionNumber(session.getConnection()));
    }
    assertEquals(before, counts());
  }

  private static Map<String, Integer> counts() throws Exception {
    final Map<String, Integer> counts = new HashMap<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var st = session.getConnection().createStatement()
    ) {
      for (String t : DatasetPartitionMapper.PARTITIONED_TABLES) {
        try (var rs = st.executeQuery("SELECT count(*) FROM " + t)) {
          rs.next();
          counts.put(t, rs.getInt(1));
        }
      }
    }
    return counts;
  }
}
