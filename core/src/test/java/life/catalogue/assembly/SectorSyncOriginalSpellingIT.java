package life.catalogue.assembly;

import life.catalogue.TestDataRules;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.mapper.*;
import life.catalogue.junit.*;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

/**
 * A sync relates a synonym spelled as in its original publication, i.e. [sic], to its accepted name by a
 * spelling correction - unless the source relates them already, which used to duplicate the relation:
 * https://github.com/CatalogueOfLife/data/issues/1815
 * An accepted name with several [sic] synonyms used to get only one of them related.
 * The synonyms are the last siblings of the attached tree, whose relations used to be lost entirely.
 */
public class SectorSyncOriginalSpellingIT extends SectorSyncTestBase {

  final static SqlSessionFactoryRule pg = new PgSetupRule();
  final static TestDataRule dataRule = TestDataRules.syncs();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pg)
      .around(dataRule)
      .around(treeRepoRule)
      .around(matchingRule)
      .around(syncFactoryRule);

  TestDataRule draftRule;

  @Before
  public void init () throws IOException, SQLException {
    draftRule = TestDataRule.draft();
    draftRule.initSession();
    draftRule.truncateDraft();
    draftRule.loadData();
    matchingRule.rematch(draftRule.testData.key);
  }

  @After
  public void after () throws IOException, SQLException {
    draftRule.getSqlSession().close();
  }

  @Test
  public void implicitSpellingCorrectionOnce() throws Exception {
    final int srcKey = dataRule.mapKey(DataFormat.COLDP, 0);
    final int user = TestDataRule.TEST_USER.getKey();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      // external source datasets have no id sequences
      var dpm = session.getMapper(DatasetPartitionMapper.class);
      dpm.createSequences(srcKey);
      dpm.updateSequences(srcKey);
      var nm = session.getMapper(NameMapper.class);
      for (var id : List.of("1006-s1", "1006-s2", "1006-s3")) {
        var n = nm.get(DSID.of(srcKey, id));
        n.setOriginalSpelling(true);
        nm.update(n);
      }
      // the source corrects the spelling of one of them already
      var nr = new NameRelation();
      nr.setDatasetKey(srcKey);
      nr.setType(NomRelType.SPELLING_CORRECTION);
      nr.setNameId("1006");
      nr.setRelatedNameId("1006-s1");
      nr.setCreatedBy(user);
      nr.setModifiedBy(user);
      session.getMapper(NameRelationMapper.class).create(nr);
    }

    NameUsageBase leontodon = getByName(srcKey, Rank.GENUS, "Leontodon");
    NameUsageBase tracheophyta = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, leontodon, tracheophyta);
    syncAll();

    var acc = getByName(Datasets.COL, Rank.SPECIES, "Leontodon taraxacoides");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var rels = session.getMapper(NameRelationMapper.class).listByName(acc.getName()).stream()
        .filter(r -> r.getType() == NomRelType.SPELLING_CORRECTION)
        .toList();
      // one copied from the source, an implicit one for each of the other two [sic] synonyms
      assertEquals(3, rels.size());
      assertEquals(3, rels.stream().map(NameRelation::getRelatedNameId).distinct().count());
    }
  }
}
