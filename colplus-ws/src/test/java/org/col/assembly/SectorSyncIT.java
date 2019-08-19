package org.col.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.List;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.NameUsageBase;
import org.col.api.model.Sector;
import org.col.api.model.SimpleName;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Origin;
import org.col.dao.DatasetImportDao;
import org.col.dao.NamesTreeDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.NameUsageMapper;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.col.db.mapper.TestDataRule;
import org.col.db.tree.TextTreePrinter;
import org.col.es.NameUsageIndexService;
import org.col.importer.PgImportRule;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Before we start any test we prepare the db with imports that can be reused across tests later on.
 */
public class SectorSyncIT {
  
  public final static PgSetupRule pg = new PgSetupRule();
  public final static TestDataRule dataRule = TestDataRule.draft();
  public final static PgImportRule importRule = PgImportRule.create(
      NomCode.BOTANICAL,
        DataFormat.ACEF,  1,
        DataFormat.COLDP, 0,
      NomCode.ZOOLOGICAL,
        DataFormat.ACEF,  5, 6, 11,
        DataFormat.COLDP, 2
  );
  public final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  @ClassRule
  public final static TestRule chain = RuleChain
      .outerRule(pg)
      .around(dataRule)
      .around(treeRepoRule)
      .around(importRule);

  DatasetImportDao diDao;
  NamesTreeDao treeDao;
  
  @Before
  public void init () throws IOException, SQLException {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    treeDao = new NamesTreeDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    // reset draft
    dataRule.truncateDraft();
    dataRule.loadData(true);
  }
  
  
  public int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }
  
  NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      List<NameUsageBase> taxa = session.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank);
      if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
      return taxa.get(0);
    }
  }
  
  NameUsageBase getByID(String id) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).get(Datasets.DRAFT_COL, id);
    }
  }
  
  private static SimpleName simple(NameUsageBase nu) {
    return new SimpleName(nu.getId(), nu.getName().canonicalNameComplete(), nu.getName().getRank());
  }
  
  static int createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, src.getDatasetKey(), simple(src), simple(target));
  }
  
  static int createSector(Sector.Mode mode, int datasetKey, SimpleName src, SimpleName target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getKey();
    }
  }
    
  void syncAll() throws IOException {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(null)) {
        sync(s);
      }
    }
  }
  
  void sync(Sector s) {
    SectorSync ss = new SectorSync(s, PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao,
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR SYNC " + s.getKey() + " ***");
    ss.run();
  }
  
  private void delete(Sector s) {
    SectorDelete sd = new SectorDelete(s, PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(),
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR DELETION " + s.getKey() + " ***");
    sd.run();
  }
  
  void print(int datasetKey) throws Exception {
    StringWriter writer = new StringWriter();
    writer.append("\nDATASET "+datasetKey+"\n");
    TextTreePrinter.dataset(datasetKey, PgSetupRule.getSqlSessionFactory(), writer).print();
    System.out.println(writer.toString());
  }
  
  void assertTree(String filename) throws IOException {
    InputStream resIn = getClass().getResourceAsStream("/assembly-trees/" + filename);
    String expected = IOUtils.toString(resIn, Charsets.UTF_8).trim();
    
    Writer writer = new StringWriter();
    TextTreePrinter.dataset(Datasets.DRAFT_COL, PgSetupRule.getSqlSessionFactory(), writer).print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
    
    // compare trees
    System.out.println("\n*** DRAFT TREE ***");
    System.out.println(tree);
    assertEquals("Assembled tree not as expected", expected, tree);
  }
  
  
  
  @Test
  public void test1_5_6() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(1, DataFormat.ACEF));
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, src, trg);
  
    src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.MERGE, src, trg);
  
    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat1_5_6.txt");
  
    NameUsageBase vogelii   = getByName(Datasets.DRAFT_COL, Rank.SUBSPECIES, "Astragalus vogelii subsp. vogelii");
    assertEquals(1, (int) vogelii.getSectorKey());
  
    NameUsageBase sp   = getByID(vogelii.getParentId());
    assertEquals(Origin.SOURCE, vogelii.getOrigin());
  }
  
  /**
   * Nested sectors
   * see https://github.com/Sp2000/colplus-backend/issues/438
   */
  @Test
  public void test6_11() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));
    
    NameUsageBase src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    final int s1 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(datasetKey(11, DataFormat.ACEF), Rank.GENUS, "Dectus");
    // target without id so far
    final int s2 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
        new SimpleName(null, "Theridiidae", Rank.FAMILY)
    );
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = sm.get(s1);
      sync(s);
      
      // update sector with now existing Theridiidae key
      NameUsageBase ther = getByName(Datasets.DRAFT_COL, Rank.FAMILY, "Theridiidae");
      s = sm.get(s2);
      s.getTarget().setId(ther.getId());
      sm.update(s);
  
      NameUsageBase u = getByName(Datasets.DRAFT_COL, Rank.FAMILY, "Theridiidae");
      assertNotNull(u);
      sync(s);
  
      u = getByName(Datasets.DRAFT_COL, Rank.SPECIES, "Dectus mascha");
      assertNotNull(u);
      assertTree("cat6_11.txt");
      
      // make sure that we can resync and still get the same results with the nested sector
      s = sm.get(s1);
      sync(s);
      assertTree("cat6_11.txt");
    }
  }
  
  /**
   * Deletion of nested sectors
   */
  @Test
  public void testDeletion() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    final int s5 = createSector(Sector.Mode.MERGE, src, trg);

    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    final int s6 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(datasetKey(11, DataFormat.ACEF), Rank.GENUS, "Dectus");
    // target without id so far
    final int s11 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
        new SimpleName(null, "Theridiidae", Rank.FAMILY)
    );
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sync(sm.get(s5));
      sync(sm.get(s6));
      
      // update sector with now existing Theridiidae key
      NameUsageBase ther = getByName(Datasets.DRAFT_COL, Rank.FAMILY, "Theridiidae");
      Sector s = sm.get(s11);
      s.getTarget().setId(ther.getId());
      sm.update(s);
      
      sync(s);
      assertTree("cat5_6_11.txt");
      
      // first we delete the merged sector which should have no nested sectors
      delete(sm.get(s5));
      assertTree("cat5_6_11_delete_5.txt");
  
      // now we delete Theridiidae with its nested genus Dectus
      delete(sm.get(s6));
      assertTree("cat5_6_11_delete_5_6.txt");
    }
  }
  
  @Test
  public void testImplicitGenus() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(0, DataFormat.COLDP));
    print(datasetKey(2, DataFormat.COLDP));
    
    NameUsageBase asteraceae   = getByName(datasetKey(0, DataFormat.COLDP), Rank.FAMILY, "Asteraceae");
    NameUsageBase tracheophyta = getByName(Datasets.DRAFT_COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, asteraceae, tracheophyta);
  
    NameUsageBase coleoptera = getByName(datasetKey(2, DataFormat.COLDP), Rank.ORDER, "Coleoptera");
    NameUsageBase insecta = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, coleoptera, insecta);
    
    syncAll();
    assertTree("cat0_2.txt");
  }
  
  @Test
  public void testKingdomSector() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(0, DataFormat.COLDP));
    
    NameUsageBase src = getByName(datasetKey(0, DataFormat.COLDP), Rank.KINGDOM, "Plantae");
    NameUsageBase plant = getByName(Datasets.DRAFT_COL, Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.MERGE, src, plant);
  
    final String plantID = plant.getId();
    assertNull(plant.getSectorKey());
    
    syncAll();
    
    assertTree("cat0.txt");
    plant = getByName(Datasets.DRAFT_COL, Rank.KINGDOM, "Plantae");
    // make sure the kingdom is not part of the sector, we merged!
    assertNull(plant.getSectorKey());
    assertEquals(plantID, plant.getId());
  }
  
}