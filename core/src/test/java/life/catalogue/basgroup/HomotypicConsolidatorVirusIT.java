package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * ICTV names parse as scientific names without any authorship, and the ICVCN has plenty of valid names
 * one edit apart, e.g. numbered species. They must never be taken as misspellings of each other,
 * while the Linnean names next to them still are.
 */
public class HomotypicConsolidatorVirusIT {
  private static final String DIR = "txtree/misspelling-virus/";

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, DIR + "tree.txtree"))
    .around(matchingRule);

  @Test
  public void virusNamesAreNoMisspellings() throws Exception {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? 0 : lnu.getSectorKey()
    );
    hc.consolidateMisspellings();
    hc.consolidate();

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      var nm = session.getMapper(NameMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);

      for (var id : new String[]{"r1", "r2", "r3"}) {
        var u = num.get(DSID.of(datasetKey, id));
        var n = nm.get(DSID.of(datasetKey, u.getName().getId()));
        assertEquals(NameType.SCIENTIFIC, n.getType());
        assertEquals(NomCode.VIRUS, n.getCode());
        assertEquals(id, TaxonomicStatus.ACCEPTED, u.getStatus());
        assertEquals("bat", u.getParentId());
        var v = vsm.getByUsage(DSID.of(datasetKey, id));
        assertTrue(v == null || !v.getIssues().contains(Issue.MISSPELLING_CONSOLIDATION));
      }

      // the Linnean misspelling is still consolidated
      var misspelt = num.get(DSID.of(datasetKey, "vul2"));
      assertEquals(TaxonomicStatus.SYNONYM, misspelt.getStatus());
      assertEquals("vul", misspelt.getParentId());
    }
  }
}
