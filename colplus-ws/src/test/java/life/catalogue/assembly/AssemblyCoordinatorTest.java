package life.catalogue.assembly;

import com.codahale.metrics.MetricRegistry;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetMapperTest;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TestDataRule;
import life.catalogue.es.name.index.NameUsageIndexService;
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
  DatasetImportDao diDao;
  
  @Before
  public void init() {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    diDao.createSuccess(Datasets.DRAFT_COL);
  
    coord = new AssemblyCoordinator(PgSetupRule.getSqlSessionFactory(), diDao, NameUsageIndexService.passThru(), new MetricRegistry());
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void scheduleEmptyDataset() throws Exception {
    Sector sector = new Sector();
    sector.setDatasetKey(Datasets.DRAFT_COL);
    sector.setMode(Sector.Mode.ATTACH);
    sector.setSubject(new SimpleName("7", "Insecta", Rank.CLASS));
    sector.setTarget(new SimpleName("123", "Arthropoda", Rank.PHYLUM));
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

    coord.sync(Datasets.DRAFT_COL, RequestScope.sector(sector.getKey()), TestEntityGenerator.USER_EDITOR);
  }
  
  @Test
  public void syncAll() throws Exception {
    Sector sector = new Sector();
    sector.setMode(Sector.Mode.ATTACH);
    sector.setDatasetKey(Datasets.DRAFT_COL);
    sector.setSubject(new SimpleName("7", "Insecta", Rank.CLASS));
    sector.setTarget(new SimpleName("123", "Arthropoda", Rank.PHYLUM));
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
    
    coord.sync(Datasets.DRAFT_COL, RequestScope.all(), TestEntityGenerator.USER_EDITOR);
  }
  
}