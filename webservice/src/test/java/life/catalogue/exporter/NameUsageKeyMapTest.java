package life.catalogue.exporter;

import life.catalogue.db.PgSetupRule;

import life.catalogue.db.TestDataRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameUsageKeyMapTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void add() {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameUsageKeyMap map = new NameUsageKeyMap(11, session);

      assertFalse(map.containsNameID("qwwert"));
      map.add("qwwert", "iop");
      assertTrue(map.containsUsageID("iop"));
      assertTrue(map.containsNameID("qwwert"));
      assertFalse(map.containsNameID("qwwerty"));

      assertFalse(map.containsNameID("name-2"));
      assertEquals("root-2", map.getFirst("name-2"));
      assertTrue(map.containsNameID("name-2"));
    }
  }
}