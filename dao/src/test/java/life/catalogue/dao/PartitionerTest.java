package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import java.sql.Connection;
import java.util.Set;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class PartitionerTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();


  @Test
  public void keys() throws Exception {
    try (Connection con = pgSetupRule.connect()) {
      Set<String> keys = Partitioner.partitionSuffices(con);
      assertEquals(Set.of("mod1", "mod0"), keys);
    }
  }

  @Test
  public void attached() throws Exception {
    try (Connection con = pgSetupRule.connect()) {
      assertTrue( Partitioner.isAttached(con, "name_3") );
      assertTrue( Partitioner.isAttached(con, "vernacular_name_3") );
      assertFalse( Partitioner.isAttached(con, "name_3567") );
      assertFalse( Partitioner.isAttached(con, "x") );
      assertFalse( Partitioner.isAttached(con, "name_dds") );
    }
  }

}