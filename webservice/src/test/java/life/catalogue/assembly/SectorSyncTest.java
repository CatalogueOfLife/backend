package life.catalogue.assembly;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.gbif.common.shaded.com.google.common.collect.Lists;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SectorSyncTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  DatasetImportDao diDao;
  
  final int datasetKey = DATASET11.getKey();
  Sector sector;
  Taxon colAttachment;
  
  @Before
  public void init() {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      // draft partition
      final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      for (int datasetKey : Lists.newArrayList(Datasets.DRAFT_COL)) {
        pm.delete(datasetKey);
        pm.create(datasetKey);
        pm.attach(datasetKey);
      }
  
      Name n = new Name();
      n.setDatasetKey(Datasets.DRAFT_COL);
      n.setUninomial("Coleoptera");
      n.setScientificName(n.getUninomial());
      n.setRank(Rank.ORDER);
      n.setId("cole");
      n.setHomotypicNameId("cole");
      n.setType(NameType.SCIENTIFIC);
      n.setOrigin(Origin.USER);
      n.applyUser(TestEntityGenerator.USER_EDITOR);
      session.getMapper(NameMapper.class).create(n);

      colAttachment = new Taxon();
      colAttachment .setId("cole");
      colAttachment.setDatasetKey(Datasets.DRAFT_COL);
      colAttachment.setStatus(TaxonomicStatus.ACCEPTED);
      colAttachment.setName(n);
      colAttachment.setOrigin(Origin.USER);
      colAttachment.applyUser(TestEntityGenerator.USER_EDITOR);
      session.getMapper(TaxonMapper.class).create(colAttachment);
  
      sector = new Sector();
      sector.setDatasetKey(Datasets.DRAFT_COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(new SimpleName("t2", "name", Rank.ORDER));
      sector.setTarget(new SimpleName("cole", "Coleoptera", Rank.ORDER));
      sector.applyUser(TestEntityGenerator.USER_EDITOR);
      session.getMapper(SectorMapper.class).create(sector);
      
      session.commit();
    }
  
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    MapperTestBase.createSuccess(Datasets.DRAFT_COL, Users.TESTER, diDao);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failNotManaged() throws Exception {
    Sector s = new Sector();
    s.setSubjectDatasetKey(datasetKey);
    s.setSubject(sector.getSubject());
    s.setTarget(sector.getTarget());
    s.applyUser(TestEntityGenerator.USER_EDITOR);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Dataset d = TestEntityGenerator.newDataset("grr");
      d.setOrigin(DatasetOrigin.RELEASED);
      d.setSourceKey(Datasets.DRAFT_COL);
      d.applyUser(Users.TESTER);
      session.getMapper(DatasetMapper.class).create(d);

      s.setDatasetKey(d.getKey());
      session.getMapper(SectorMapper.class).create(s);
    }

    // this should fail
    SectorSync ss = new SectorSync(s.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao,
      SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestEntityGenerator.USER_EDITOR);

  }

  @Test
  public void sync() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final NameMapper nm = session.getMapper(NameMapper.class);
      assertEquals(1, nm.count(Datasets.DRAFT_COL));
    }

    SectorSync ss = new SectorSync(sector.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao,
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestEntityGenerator.USER_EDITOR);
    ss.run();

    MapperTestBase.createSuccess(Datasets.DRAFT_COL, Users.TESTER, diDao);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final NameMapper nm = session.getMapper(NameMapper.class);
      assertEquals(24, nm.count(Datasets.DRAFT_COL));
  
      final TaxonMapper tm = session.getMapper(TaxonMapper.class);
      final SynonymMapper sm = session.getMapper(SynonymMapper.class);
      assertEquals(1, tm.countRoot(Datasets.DRAFT_COL));
      assertEquals(20, tm.count(Datasets.DRAFT_COL));
      assertEquals(4, sm.count(Datasets.DRAFT_COL));
      
      List<Taxon> taxa = tm.list(Datasets.DRAFT_COL, new Page(0, 100));
      assertEquals(20, taxa.size());
      
      final VernacularNameMapper vm = session.getMapper(VernacularNameMapper.class);
      List<VernacularName> vNames = new ArrayList<>();
      for (Taxon t : taxa) {
        vNames.addAll(vm.listByTaxon(DSID.draftID(t.getId())));
      }
      assertEquals(3, vNames.size());
  
      final DistributionMapper dm = session.getMapper(DistributionMapper.class);
      List<Distribution> distributions = new ArrayList<>();
      for (Taxon t : taxa) {
        distributions.addAll(dm.listByTaxon(DSID.draftID(t.getId())));
      }
      assertEquals(7, distributions.size());
    }

    // try now with a "virtual" sector source
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final SectorMapper sm = session.getMapper(SectorMapper.class);
      // sync unassigned genera (unassigned family) in order Carnivora
      sector.setPlaceholderRank(Rank.FAMILY);
      sector.getSubject().setId("t4");
      sm.update(sector);
    }

    ss = new SectorSync(sector.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao,
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestEntityGenerator.USER_EDITOR);
    ss.run();

  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CopletableFutures.
   */
  static void successCallBack(SectorRunnable sync) {
    System.out.println("Sector Sync success");
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CopletableFutures.
   */
  static void errorCallBack(SectorRunnable sync, Exception err) {
    System.out.println("Sector Sync failed:");
    err.printStackTrace();
    fail("Sector sync failed");
  }

}