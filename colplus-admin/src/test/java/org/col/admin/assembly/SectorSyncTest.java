package org.col.admin.assembly;

import org.apache.ibatis.session.SqlSession;
import org.col.api.TestEntityGenerator;
import org.col.api.model.*;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Origin;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.col.db.mapper.*;
import org.gbif.common.shaded.com.google.common.collect.Lists;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SectorSyncTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final InitMybatisRule initMybatisRule = InitMybatisRule.tree();
  
  DatasetImportDao diDao;
  
  final int datasetKey = DATASET11.getKey();
  Sector sector;
  Taxon colAttachment;
  
  @Before
  public void init() {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      // draft & name index partition
      final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      for (int datasetKey : Lists.newArrayList(Datasets.DRAFT_COL, Datasets.PCAT)) {
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
      colAttachment.setName(n);
      colAttachment.setOrigin(Origin.USER);
      colAttachment.applyUser(TestEntityGenerator.USER_EDITOR);
      session.getMapper(TaxonMapper.class).create(colAttachment);
  
      sector = new Sector();
      sector.setDatasetKey(datasetKey);
      sector.setSubject(new SimpleName("t2", "name", Rank.ORDER));
      sector.setTarget(new SimpleName("cole", "Coleoptera", Rank.ORDER));
      sector.applyUser(TestEntityGenerator.USER_EDITOR);
      session.getMapper(SectorMapper.class).create(sector);
      
      session.commit();
    }
  
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory());
    diDao.createSuccess(Datasets.DRAFT_COL);
  }
  
  @Test
  public void sync() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final NameMapper nm = session.getMapper(NameMapper.class);
      assertEquals(1, nm.count(Datasets.DRAFT_COL));
    }

    SectorSync ss = new SectorSync(sector.getKey(), PgSetupRule.getSqlSessionFactory(), null,
        this::successCallBack, this::errorCallBack, TestEntityGenerator.USER_EDITOR);
    ss.sync();
  
    diDao.createSuccess(Datasets.DRAFT_COL);
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final NameMapper nm = session.getMapper(NameMapper.class);
      assertEquals(20, nm.count(Datasets.DRAFT_COL));
  
      final TaxonMapper tm = session.getMapper(TaxonMapper.class);
      assertEquals(1, tm.countRoot(Datasets.DRAFT_COL));
      assertEquals(20, tm.count(Datasets.DRAFT_COL));
    }
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CopletableFutures.
   */
  private void successCallBack(SectorSync sync) {
    System.out.println("Sector Sync success");
  }
  
  /**
   * We use old school callbacks here as you cannot easily cancel CopletableFutures.
   */
  private void errorCallBack(SectorSync sync, Exception err) {
    System.out.println("Sector Sync failed:");
    err.printStackTrace();
    fail("Sector sync failed");
  }
}