package life.catalogue.exporter;

import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

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
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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

  /**
   * The reverse usage id index costs one entry per usage, so an export that has no bare names to
   * de-clash does not build it at all and must say so rather than answer wrongly.
   */
  @Test
  public void usageIDsNotTracked() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      NameUsageKeyMap map = new NameUsageKeyMap(11, session, false);

      map.add("qwwert", "iop");
      assertEquals("iop", map.getFirst("qwwert"));
      assertTrue(map.containsNameID("qwwert"));
      assertThrows(IllegalStateException.class, () -> map.containsUsageID("iop"));
    }
  }

  /**
   * A name with no usage at all left no trace in either map, so every lookup of it re-ran the same
   * fruitless query. The apple data holds exactly one such bare name.
   */
  @Test
  public void bareNameMissesAreRemembered() {
    final String bareNameID = "http://services.snsb.info/DTNtaxonlists/rest/v0.1/names/DiversityTaxonNames_Insecta/5009538/";
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      NameUsageKeyMap map = new NameUsageKeyMap(11, session);

      assertNull(map.getFirst(bareNameID));
      assertTrue(map.usageIDs(bareNameID).isEmpty());
      // still a miss, and still not confused with a name that does have a usage
      assertNull(map.getFirst(bareNameID));
      assertEquals("root-1", map.getFirst("name-1"));
    }
  }
}