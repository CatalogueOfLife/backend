package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TxtTreeDataRule;

import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.Rank;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class TreeCleanerAndValidatorIT {

  final static int datasetKey = 100;

  public final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(TestDataRule.empty())
    .around(new TxtTreeDataRule(datasetKey, "txtree/validation/mismatch.txtree"));

  @Test
  public void parentMismatch() throws IOException {
    final var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    try (SqlSession session = factory.openSession(true);
         TreeCleanerAndValidator tcv = new TreeCleanerAndValidator(factory, datasetKey, false)
    ) {
      var num = session.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(datasetKey);
      params.setSynonyms(false);

      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), tcv);
    }

    // start tests
    assertIssues(Rank.SPECIES, "Diamesa aberrata");
    assertIssues(Rank.SPECIES, "Burundi negeriana", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Diamessa kundera", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Nesodiamesa negeriana", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Ablabesmyia suturalis", Issue.MISSING_GENUS);
    assertIssues(Rank.SPECIES, "Ablabesmyia satanis", Issue.MISSING_GENUS);

    assertIssues(Rank.GENUS, "Onychodiamesa");

    assertIssues(Rank.ORDER, "Heminoptera", Issue.CLASSIFICATION_RANK_ORDER_INVALID);
    assertIssues(Rank.ORDER, "Hymenoidales", Issue.CLASSIFICATION_RANK_ORDER_INVALID);

    assertIssues(Rank.ORDER, "Hymenoptera");
    assertIssues(Rank.UNRANKED, "Hymenoidies");
    assertIssues(Rank.SUBORDER, "Hymenoidaloides");
    assertIssues(Rank.FAMILY, "Hymenoidaloidea", Issue.RANK_NAME_SUFFIX_CONFLICT);
    assertIssues(Rank.UNRANKED, "Hymenoidalododes");
    assertIssues(Rank.SUBFAMILY, "Hymenoidaloidiea");
  }

  void assertIssues(Rank rank, String name, Issue ... issues) {
    var u = SectorSyncIT.getByName(datasetKey, rank, name);
    assertNotNull(u);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var vm = session.getMapper(VerbatimSourceMapper.class);
      var v = vm.get(DSID.of(datasetKey, u.getId()));
      if (issues == null || issues.length == 0) {
        assertFalse(v != null && v.hasIssues());
      } else {
        assertEquals(issues.length, v.getIssues().size());
        for (var iss : issues) {
          assertTrue("Issue "+iss+" missing from "+name, v.getIssues().contains(iss));
        }
      }
    }
  }

}