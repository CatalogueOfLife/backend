package life.catalogue.assembly;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.SectorMapper;

import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.codahale.metrics.MetricRegistry;

public class AssemblyCoordinatorTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  public final TestDataRule testDataRule = TestDataRule.draft();
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  public final NameMatchingRule matchingRule = new NameMatchingRule();
  public final SyncFactoryRule syncFactoryRule = new SyncFactoryRule();
  @Rule
  public final TestRule classRules = RuleChain
    .outerRule(testDataRule)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  SyncManager coord;

  @Before
  public void init() {
    MapperTestBase.createSuccess(Datasets.COL, Users.TESTER, syncFactoryRule.getDiDao());

    coord = new SyncManager(SqlSessionFactoryRule.getSqlSessionFactory(), NameMatchingRule.getIndex(), SyncFactoryRule.getFactory(), new MetricRegistry());
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void scheduleEmptyDataset() throws Exception {
    Sector sector = new Sector();
    sector.setDatasetKey(Datasets.COL);
    sector.setMode(Sector.Mode.ATTACH);
    sector.setSubject(SimpleNameLink.of("7", "Insecta", Rank.CLASS));
    sector.setTarget(SimpleNameLink.of("123", "Arthropoda", Rank.PHYLUM));
    sector.applyUser(TestEntityGenerator.USER_EDITOR);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      final DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = DatasetMapperTest.create();
      testDataRule.getKeyGenerator().setKey(d);
      dm.create(d);
      
      final SectorMapper sm = session.getMapper(SectorMapper.class);
      // point to bad dataset
      sector.setSubjectDatasetKey(d.getKey());
      sm.create(sector);
    }

    coord.sync(Datasets.COL, RequestScope.sector(sector), TestEntityGenerator.USER_EDITOR);
  }
  
  @Test
  public void syncAll() throws Exception {
    Sector sector = new Sector();
    sector.setMode(Sector.Mode.ATTACH);
    sector.setDatasetKey(Datasets.COL);
    sector.setSubject(SimpleNameLink.of("7", "Insecta", Rank.CLASS));
    sector.setTarget(SimpleNameLink.of("123", "Arthropoda", Rank.PHYLUM));
    sector.applyUser(TestEntityGenerator.USER_EDITOR);
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      final DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = DatasetMapperTest.create();
      dm.create(d);
      
      final SectorMapper sm = session.getMapper(SectorMapper.class);
      // point to bad dataset
      sector.setSubjectDatasetKey(d.getKey());
      sm.create(sector);
    }
    
    coord.sync(Datasets.COL, RequestScope.all(), TestEntityGenerator.USER_EDITOR);
  }
  
}