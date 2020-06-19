package life.catalogue.assembly;

import com.google.common.base.Charsets;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.importer.PgImportRule;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        DataFormat.COLDP, 2, 4,
      NomCode.VIRUS,
        DataFormat.ACEF,  14
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
  TaxonDao tdao;
  
  @Before
  public void init () throws IOException, SQLException {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    treeDao = new NamesTreeDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    // reset draft
    dataRule.truncateDraft();
    dataRule.loadData(true);
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru());
  }
  
  
  public int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }
  
  public static NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      List<NameUsageBase> taxa = session.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank);
      if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
      return taxa.get(0);
    }
  }
  
  NameUsageBase getByID(String id) {
    return getByID(Datasets.DRAFT_COL, id);
  }
  
  NameUsageBase getByID(int datasetKey, String id) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).get(DSID.of(datasetKey, id));
    }
  }

  Taxon getDraftTaxonBySourceID(int sourceDatasetKey, String id) {
    Taxon src;
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      src = tm.get(DSID.of(sourceDatasetKey, id));
    }
    return (Taxon) getByName(Datasets.DRAFT_COL, src.getName().getRank(), src.getName().getScientificName());
  }

  private static SimpleName simple(NameUsageBase nu) {
    return new SimpleName(nu.getId(), nu.getName().getLabel(), nu.getName().getRank());
  }
  
  public static int createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, src.getDatasetKey(), simple(src), simple(target));
  }

  public static int createSector(Sector.Mode mode, int datasetKey, SimpleName src, SimpleName target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setDatasetKey(Datasets.DRAFT_COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector.getId();
    }
  }

  public static EditorialDecision createDecision(int datasetKey, SimpleName src, EditorialDecision.Mode mode, Name name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision ed = new EditorialDecision();
      ed.setMode(mode);
      ed.setDatasetKey(Datasets.DRAFT_COL);
      ed.setSubjectDatasetKey(datasetKey);
      ed.setSubject(src);
      ed.setName(name);
      ed.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(ed);
      return ed;
    }
  }

  public void syncAll() {
    syncAll(diDao);
  }

  public static void syncAll(DatasetImportDao diDao) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(Datasets.DRAFT_COL, null)) {
        sync(s, diDao);
      }
    }
  }

  void sync(Sector s) {
    sync(s, diDao);
  }

  static void sync(Sector s, DatasetImportDao diDao) {
    SectorSync ss = new SectorSync(s.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), diDao,
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR SYNC " + s.getKey() + " ***");
    ss.run();
  }
  
  private void deleteFull(Sector s) {
    SectorDeleteFull sd = new SectorDeleteFull(s.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(),
        SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR FULL DELETION " + s.getKey() + " ***");
    sd.run();
  }

  private void delete(Sector s) {
    SectorDelete sd = new SectorDelete(s.getId(), PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), treeDao,
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
  
  
  private void assertHasVerbatimName(NameUsage u) {
    assertNotNull(u.getName().getVerbatimKey());
  }

  @Test
  public void test1_5_6() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(1, DataFormat.ACEF));
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.PHYLUM, "Tracheophyta");
    int s1 = createSector(Sector.Mode.ATTACH, src, trg);
  
    src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    int s2 = createSector(Sector.Mode.UNION, src, trg);
  
    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    int s3 = createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat1_5_6.txt");
  
    Taxon vogelii = (Taxon) getByName(Datasets.DRAFT_COL, Rank.SUBSPECIES, "Astragalus vogelii subsp. vogelii");
    assertEquals(s1, (int) vogelii.getSectorKey());
    assertHasVerbatimName(vogelii);

    Taxon sp = (Taxon) getByID(vogelii.getParentId());
    assertEquals(Origin.SOURCE, vogelii.getOrigin());
    assertHasVerbatimName(sp);

    TaxonInfo ti = tdao.getTaxonInfo(sp);

    Reference r = ti.getReference(sp.getName().getPublishedInId());
    assertEquals(sp.getDatasetKey(), r.getDatasetKey());
    assertEquals(sp.getSectorKey(), r.getSectorKey());
    assertEquals("Greuter,W. et al. (Eds.). Med-Checklist Vol.4 (published). (1989).", r.getCitation());
    assertEquals(2, ti.getReferences().size());

    final int s1dk = datasetKey(1, DataFormat.ACEF);
    Taxon t = getDraftTaxonBySourceID(s1dk, "13287");
    assertEquals(Datasets.DRAFT_COL, (int) t.getDatasetKey());
    // 19 vernaculars, 5 distinct refs
    ti = tdao.getTaxonInfo(t);
    assertEquals(19, ti.getVernacularNames().size());
    Set<String> keys = new HashSet<>();
    for (VernacularName vn : ti.getVernacularNames()) {
      assertEquals(Datasets.DRAFT_COL, (int) vn.getDatasetKey());
      assertNotNull(vn.getName());
      if (vn.getReferenceId() != null) {
        r = ti.getReference(vn.getReferenceId());
        keys.add(r.getId());
        assertEquals(Datasets.DRAFT_COL, (int) r.getDatasetKey());
        assertEquals(t.getSectorKey(), r.getSectorKey());
        assertNotNull(r.getCitation());
      }
    }
    assertEquals(5, keys.size());
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/493
   */
  @Test
  public void testDecisions() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(4, DataFormat.COLDP));
    final int d4key = datasetKey(4, DataFormat.COLDP);
  
    NameUsageBase coleoptera = getByName(d4key, Rank.ORDER, "Coleoptera");
    NameUsageBase insecta = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, coleoptera, insecta);
    
    NameUsageBase src = getByID(d4key, "12");
    createDecision(d4key, simple(src), EditorialDecision.Mode.BLOCK, null);
  
    src = getByID(d4key, "11");
    Name newName = new Name();
    newName.setScientificName("Euplectus cavicollis");
    newName.setAuthorship("LeConte, J. L., 1878");
    createDecision(d4key, simple(src), EditorialDecision.Mode.UPDATE, newName);
    
    syncAll();
    assertTree("cat4.txt");
  
    NameUsageBase eucav = getByName(Datasets.DRAFT_COL, Rank.SPECIES, "Euplectus cavicollis");
    assertEquals("Euplectus cavicollis", eucav.getName().getScientificName());
    assertEquals(NameType.SCIENTIFIC, eucav.getName().getType());
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
      assertNotNull(ther);
      s = sm.get(s2);
      s.getTarget().setId(ther.getId());
      sm.update(s);

      sync(s);

      NameUsageBase u = getByName(Datasets.DRAFT_COL, Rank.SPECIES, "Dectus mascha");
      assertNotNull(u);
      assertEquals((int)u.getSectorKey(), s2);
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
  public void testDeletionFull() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    final int s5 = createSector(Sector.Mode.UNION, src, trg);

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
      deleteFull(sm.get(s5));
      assertTree("cat5_6_11_delete_full_5.txt");
  
      // now we delete Theridiidae with its nested genus Dectus
      deleteFull(sm.get(s6));
      assertTree("cat5_6_11_delete_full_5_6.txt");
    }
  }

  /**
   * Deletion of nested sectors only removing species
   */
  @Test
  public void testDeletion() throws Exception {
    print(Datasets.DRAFT_COL);
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));

    NameUsageBase src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.CLASS, "Insecta");
    final int s5 = createSector(Sector.Mode.UNION, src, trg);

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
    createSector(Sector.Mode.UNION, src, plant);
  
    final String plantID = plant.getId();
    assertNull(plant.getSectorKey());
    
    syncAll();
    
    assertTree("cat0.txt");
    plant = getByName(Datasets.DRAFT_COL, Rank.KINGDOM, "Plantae");
    // make sure the kingdom is not part of the sector, we merged!
    assertNull(plant.getSectorKey());
    assertEquals(plantID, plant.getId());
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/452
   */
  @Test
  public void testVirus() throws Exception {
    print(datasetKey(14, DataFormat.ACEF));
    
    NameUsageBase src = getByName(datasetKey(14, DataFormat.ACEF), Rank.KINGDOM, "Viruses");
    NameUsageBase trg = getByName(Datasets.DRAFT_COL, Rank.KINGDOM, "Viruses");
    createSector(Sector.Mode.UNION, src, trg);
    
    syncAll();
    
    assertTree("cat14.txt");
  }
  
}