package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleName;
import life.catalogue.junit.*;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Map;

import static org.junit.Assert.*;

public class NameUsageMapperNidxTreeTest {
  static final int datasetKey = 100;

  public static SqlSessionFactoryRule pgRule = new PgSetupRule();
  public static TxtTreeDataRule dataRule = TxtTreeDataRule.create(Map.of(
      datasetKey, TxtTreeDataRule.TreeData.MAMMALIA
  ), false, true);
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pgRule)
      .around(treeRepoRule)
      .around(dataRule)
      .around(matchingRule);

  int findNidx(Rank rank, String name) {
    var m = NameMatchingRule.getIndex().match(new SimpleName(name, name, rank), false, false);
    return m.getNameKey();
  }

  @Test
  public void listByNamesIndexIDGlobalClassified() {
    try (var session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      // synonym genus Lupulus
      var res = num.listByNamesIndexIDGlobalClassified( findNidx(Rank.GENUS, "Lupulus"), new Page());
      assertEquals(1, res.size());
      var m = res.getFirst();
      assertTrue(m.isSynonym());
      assertEquals("Canis", m.getAccepted().getLabel());
      assertFalse(m.getClassification().isEmpty());
      assertEquals(4, m.getClassification().size());
      assertEquals("Chordata", m.getClassification().getFirst().getLabel());
      assertEquals("Canidae", m.getClassification().getLast().getLabel());
    }
  }

}