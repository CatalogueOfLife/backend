package life.catalogue.assembly;

import com.codahale.metrics.MetricRegistry;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AssemblyCoordinatorTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.draft();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  AssemblyCoordinator coord;

  @Before
  public void init() {
    DatasetImportDao diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    MapperTestBase.createSuccess(Datasets.COL, Users.TESTER, diDao);

    SectorImportDao sid = new SectorImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    coord = new AssemblyCoordinator(PgSetupRule.getSqlSessionFactory(), NameIndexFactory.passThru(), sid, NameUsageIndexService.passThru(), new MetricRegistry());
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void scheduleEmptyDataset() throws Exception {
    Sector sector = new Sector();
    sector.setDatasetKey(Datasets.COL);
    sector.setMode(Sector.Mode.ATTACH);
    sector.setSubject(SimpleNameLink.of("7", "Insecta", Rank.CLASS));
    sector.setTarget(SimpleNameLink.of("123", "Arthropoda", Rank.PHYLUM));
    sector.applyUser(TestEntityGenerator.USER_EDITOR);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = DatasetMapperTest.create();
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
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
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