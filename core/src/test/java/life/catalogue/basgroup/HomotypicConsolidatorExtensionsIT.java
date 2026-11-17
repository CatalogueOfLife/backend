package life.catalogue.basgroup;

import life.catalogue.junit.*;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A usage the consolidation deletes - a duplicate it has just turned into a synonym - must take its extensions along.
 * They used to stay behind pointing to no usage, which prod, lacking the foreign key, took: Terminalia igaratensis in
 * 316691 (COL26.10 XR) was converted into a synonym of Terminalia kleinii, which already had that synonym, and deleted,
 * leaving its distribution behind and breaking the release bundle.
 */
public class HomotypicConsolidatorExtensionsIT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, "txtree/homconsolidation-extensions.txtree"))
    .around(matchingRule);

  @Test
  public void deletedUsagesTakeTheirExtensions() throws Exception {
    final int before = count("SELECT count(*) FROM name_usage WHERE dataset_key = " + datasetKey);
    exec("INSERT INTO distribution (id, dataset_key, taxon_id, gazetteer, area, created_by, modified_by)"
      + " SELECT row_number() OVER (), dataset_key, id, 'TEXT', 'Brazil', 0, 0 FROM name_usage"
      + " WHERE dataset_key = " + datasetKey + " AND status = 'ACCEPTED'");
    exec("INSERT INTO vernacular_name (id, dataset_key, taxon_id, name, created_by, modified_by)"
      + " SELECT row_number() OVER (), dataset_key, id, 'amendoeira', 0, 0 FROM name_usage"
      + " WHERE dataset_key = " + datasetKey + " AND status = 'ACCEPTED'");

    HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    ).consolidate();

    assertTrue("the consolidation must delete usages for this to test anything",
      count("SELECT count(*) FROM name_usage WHERE dataset_key = " + datasetKey) < before);
    for (String table : new String[]{"distribution", "vernacular_name"}) {
      assertEquals(table + " records left without their usage", 0, count("SELECT count(*) FROM " + table + " x"
        + " WHERE x.dataset_key = " + datasetKey
        + " AND NOT EXISTS (SELECT 1 FROM name_usage u WHERE u.dataset_key = x.dataset_key AND u.id = x.taxon_id)"));
    }
  }

  private static void exec(String sql) throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var st = session.getConnection().createStatement()) {
      st.execute(sql);
    }
  }

  private static int count(String sql) throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var st = session.getConnection().createStatement();
         var rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }
}
