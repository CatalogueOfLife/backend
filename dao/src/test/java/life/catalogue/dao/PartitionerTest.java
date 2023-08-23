package life.catalogue.dao;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import java.sql.Connection;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PartitionerTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

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