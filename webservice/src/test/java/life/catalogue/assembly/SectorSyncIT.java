package life.catalogue.assembly;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.*;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.importer.PgImportRule;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Testing SectorSync but also SectorDelete and SectorDeleteFull.
 * The test takes some time and prepares various sources for all tests, hence we test deletions here too avoiding duplication of the time consuming overhead.
 *
 * Before we start any test we prepare the db with imports that can be reused across tests later on.
 */
public class SectorSyncIT {
  
  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();
  final static TestDataRule dataRule = TestDataRule.draft();
  final static PgImportRule importRule = PgImportRule.create(
//  NomCode.BOTANICAL,
//    DataFormat.ACEF,  1,
//    DataFormat.COLDP, 0, 22, 25,
//    DataFormat.DWCA, 1, 2,
//  NomCode.ZOOLOGICAL,
//    DataFormat.ACEF,  5, 6, 11,
//    DataFormat.COLDP, 2, 4, 14, 24, 26, 27,
//  NomCode.VIRUS,
//    DataFormat.ACEF,  14

    NomCode.ZOOLOGICAL,
      DataFormat.COLDP, 27
  );

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pg)
      .around(dataRule)
      .around(treeRepoRule)
      .around(importRule)
      .around(matchingRule)
      .around(syncFactoryRule);

  TaxonDao tdao;


  @Before
  public void init () throws IOException, SQLException {
    // reset draft
    dataRule.truncateDraft();
    dataRule.loadData();
    // rematch draft
    matchingRule.rematch(dataRule.testData.key);
    tdao = syncFactoryRule.getTdao();
  }

  public int datasetKey(int key, DataFormat format) {
    return importRule.datasetKey(key, format);
  }
  
  public static NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      List<NameUsageBase> taxa = session.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank, new Page(0,100));
      if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
      return taxa.get(0);
    }
  }

  public static VerbatimSource getSource(DSID<String> key) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      return session.getMapper(VerbatimSourceMapper.class).get(key);
    }
  }
  
  NameUsageBase getByID(String id) {
    return getByID(Datasets.COL, id);
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
    return (Taxon) getByName(Datasets.COL, src.getName().getRank(), src.getName().getScientificName());
  }

  private static SimpleNameLink simple(NameUsageBase nu) {
    return nu == null ? null : nu.toSimpleNameLink();
  }
  
  public static DSID<Integer> createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, src.getDatasetKey(), simple(src), simple(target));
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, datasetKey, simple(src), simple(target));
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    return createSector(mode, null, datasetKey, src, target);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, Integer priority, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setPriority(priority);
      sector.setDatasetKey(Datasets.COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.setEntities(Set.of(EntityType.VERNACULAR, EntityType.DISTRIBUTION, EntityType.REFERENCE));
      sector.applyUser(TestDataRule.TEST_USER);
      session.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  public static EditorialDecision createDecision(int datasetKey, SimpleNameLink src, EditorialDecision.Mode mode, Name name) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      EditorialDecision ed = new EditorialDecision();
      ed.setMode(mode);
      ed.setDatasetKey(Datasets.COL);
      ed.setSubjectDatasetKey(datasetKey);
      ed.setSubject(src);
      ed.setName(name);
      ed.applyUser(TestDataRule.TEST_USER);
      session.getMapper(DecisionMapper.class).create(ed);
      return ed;
    }
  }

  public void syncAll() {
    syncAll(null);
  }

  public void syncMergesOnly() {
    syncAll(s -> s.getMode() == Sector.Mode.MERGE);
  }

  public static void syncAll(@Nullable Predicate<Sector> filter) {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(Datasets.COL, null)) {
        if (filter == null || filter.test(s)) {
          sync(s);
        }
      }
    }
  }

  /**
   * Syncs into the project
   */
  static void sync(Sector s) {
    SectorSync ss = SyncFactoryRule.getFactory().project(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    runSync(ss);
  }

  private static void runSync(SectorSync ss) {
    System.out.println("\n*** SECTOR " + ss.sector.getMode() + " SYNC " + ss.sectorKey + " ***");
    ss.run();
    if (ss.getState().getState() != ImportState.FINISHED){
      throw new IllegalStateException("SectorSync failed with error: " + ss.getState().getError());
    }
  }
  private void deleteFull(Sector s) {
    SectorDeleteFull sd = SyncFactoryRule.getFactory().deleteFull(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR FULL DELETION " + s.getKey() + " ***");
    sd.run();
  }

  private void delete(Sector s) {
    SectorDelete sd = SyncFactoryRule.getFactory().delete(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    System.out.println("\n*** SECTOR DELETION " + s.getKey() + " ***");
    sd.run();
  }

  void print(int datasetKey) throws Exception {
    StringWriter writer = new StringWriter();
    writer.append("\nDATASET "+datasetKey+"\n");
    PrinterFactory.dataset(TextTreePrinter.class, datasetKey, PgSetupRule.getSqlSessionFactory(), writer).print();
    System.out.println(writer.toString());
  }

  void print(String filename) throws Exception {
    System.out.println("\n" + filename);
    InputStream resIn = openResourceStream(filename);
    String tree = UTF8IoUtils.readString(resIn).trim();
    System.out.println(tree);
  }

  InputStream openResourceStream(String filename) {
    return getClass().getResourceAsStream("/assembly-trees/" + filename);
  }

  void assertTree(String filename) throws IOException {
    InputStream resIn = openResourceStream(filename);
    String expected = UTF8IoUtils.readString(resIn).trim();
    
    Writer writer = new StringWriter();
    PrinterFactory.dataset(TextTreePrinter.class, Datasets.COL, PgSetupRule.getSqlSessionFactory(), writer).print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
    
    // compare trees
    System.out.println("\n*** DRAFT TREE ***");
    System.out.println(tree);
    assertEquals("Assembled tree not as expected", expected, tree);
  }
  
  
  private void assertHasVerbatimSource(DSID<String> id, String expectedSourceId) {
    VerbatimSource v = getSource(id);
    assertEquals(id.getId(), v.getId());
    assertEquals(id.getDatasetKey(), v.getDatasetKey());
    assertNotNull(v.getSourceDatasetKey());
    assertEquals(expectedSourceId, v.getSourceId());
  }

  /**
   * https://github.com/gbif/checklistbank/issues/187
   */
  @Test
  public void culex() throws Exception {
    print(Datasets.COL);
    print(datasetKey(14, DataFormat.COLDP));

    NameUsageBase src = getByName(datasetKey(14, DataFormat.COLDP), Rank.ORDER, "Diptera");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat14b.txt");
  }

  /**
   * https://github.com/CatalogueOfLife/testing/issues/189
   */
  @Test
  public void wcvpInfraspecies() throws Exception {
    print(Datasets.COL);
    print(datasetKey(22, DataFormat.COLDP));

    NameUsageBase src = getByName(datasetKey(22, DataFormat.COLDP), Rank.FAMILY, "Acoraceae");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat22.txt");
  }

  @Test
  public void test1_5_6() throws Exception {
    print(Datasets.COL);
    print(datasetKey(1, DataFormat.ACEF));
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(1, DataFormat.ACEF), Rank.ORDER, "Fabales");
    NameUsageBase trg = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    DSID<Integer> s1 = DSID.copy(createSector(Sector.Mode.ATTACH, src, trg));
  
    src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.UNION, src, trg);
  
    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat1_5_6.txt");
  
    Taxon vogelii = (Taxon) getByName(Datasets.COL, Rank.SUBSPECIES, "Astragalus vogelii subsp. vogelii");
    assertEquals(s1, vogelii.getSectorDSID());
    assertHasVerbatimSource(vogelii, "5177");

    Taxon sp = (Taxon) getByID(vogelii.getParentId());
    assertEquals(Origin.SOURCE, vogelii.getOrigin());
    assertHasVerbatimSource(sp, "5175");

    TaxonInfo ti = tdao.getTaxonInfo(sp);

    Reference r = ti.getReference(sp.getName().getPublishedInId());
    assertEquals(sp.getDatasetKey(), r.getDatasetKey());
    assertEquals(sp.getSectorKey(), r.getSectorKey());
    assertEquals("Greuter,W. et al. (1989). Med-Checklist Vol.4 (Published).", r.getCitation());
    assertEquals(2, ti.getReferences().size());

    final int s1dk = datasetKey(1, DataFormat.ACEF);
    Taxon t = getDraftTaxonBySourceID(s1dk, "13287");
    assertEquals(Datasets.COL, (int) t.getDatasetKey());
    // 19 vernaculars, 5 distinct refs
    ti = tdao.getTaxonInfo(t);
    assertEquals(19, ti.getVernacularNames().size());
    Set<String> keys = new HashSet<>();
    for (VernacularName vn : ti.getVernacularNames()) {
      assertEquals(Datasets.COL, (int) vn.getDatasetKey());
      assertNotNull(vn.getName());
      if (vn.getReferenceId() != null) {
        r = ti.getReference(vn.getReferenceId());
        keys.add(r.getId());
        assertEquals(Datasets.COL, (int) r.getDatasetKey());
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
    print(Datasets.COL);
    print(datasetKey(4, DataFormat.COLDP));
    final int d4key = datasetKey(4, DataFormat.COLDP);
  
    NameUsageBase coleoptera = getByName(d4key, Rank.ORDER, "Coleoptera");
    NameUsageBase insecta = getByName(Datasets.COL, Rank.CLASS, "Insecta");
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
  
    NameUsageBase eucav = getByName(Datasets.COL, Rank.SPECIES, "Euplectus cavicollis");
    assertEquals("Euplectus cavicollis", eucav.getName().getScientificName());
    assertEquals(NameType.SCIENTIFIC, eucav.getName().getType());
  }

  /**
   * Nested sectors
   * see https://github.com/Sp2000/colplus-backend/issues/438
   */
  @Test
  public void test6_11() throws Exception {
    print(Datasets.COL);
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));
    
    NameUsageBase src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s1 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(datasetKey(11, DataFormat.ACEF), Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s2 = DSID.copy(createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    ));
  
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      Sector s = sm.get(s1);
      sync(s);
      
      // update sector with now existing Theridiidae key
      NameUsageBase ther = getByName(Datasets.COL, Rank.FAMILY, "Theridiidae");
      assertNotNull(ther);
      s = sm.get(s2);
      s.getTarget().setId(ther.getId());
      sm.update(s);

      sync(s);

      NameUsageBase u = getByName(Datasets.COL, Rank.SPECIES, "Dectus mascha");
      assertNotNull(u);
      assertEquals(u.getSectorDSID(), s2);
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
    print(Datasets.COL);
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));
  
    NameUsageBase src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s5 = createSector(Sector.Mode.UNION, src, trg);

    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s6 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(datasetKey(11, DataFormat.ACEF), Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s11 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    );
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sync(sm.get(s5));
      sync(sm.get(s6));
      
      // update sector with now existing Theridiidae key
      NameUsageBase ther = getByName(Datasets.COL, Rank.FAMILY, "Theridiidae");
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
    print(Datasets.COL);
    print(datasetKey(5, DataFormat.ACEF));
    print(datasetKey(6, DataFormat.ACEF));
    print(datasetKey(11, DataFormat.ACEF));

    NameUsageBase src = getByName(datasetKey(5, DataFormat.ACEF), Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s5 = createSector(Sector.Mode.UNION, src, trg);

    src = getByName(datasetKey(6, DataFormat.ACEF), Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s6 = createSector(Sector.Mode.ATTACH, src, trg);

    src = getByName(datasetKey(11, DataFormat.ACEF), Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s11 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    );

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sync(sm.get(s5));
      sync(sm.get(s6));

      // update sector with now existing Theridiidae key
      NameUsageBase ther = getByName(Datasets.COL, Rank.FAMILY, "Theridiidae");
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
    print(Datasets.COL);
    print(datasetKey(0, DataFormat.COLDP));
    print(datasetKey(2, DataFormat.COLDP));
    
    NameUsageBase asteraceae   = getByName(datasetKey(0, DataFormat.COLDP), Rank.FAMILY, "Asteraceae");
    NameUsageBase tracheophyta = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, asteraceae, tracheophyta);
  
    NameUsageBase coleoptera = getByName(datasetKey(2, DataFormat.COLDP), Rank.ORDER, "Coleoptera");
    NameUsageBase insecta = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, coleoptera, insecta);
    
    syncAll();
    assertTree("cat0_2.txt");
  }
  
  @Test
  public void testKingdomSector() throws Exception {
    print(Datasets.COL);
    print(datasetKey(0, DataFormat.COLDP));
    
    NameUsageBase src = getByName(datasetKey(0, DataFormat.COLDP), Rank.KINGDOM, "Plantae");
    NameUsageBase plant = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.UNION, src, plant);
  
    final String plantID = plant.getId();
    assertNull(plant.getSectorKey());
    
    syncAll();
    // Paulownia × tomentosa f. pasta is a provisional name and should not create implicit taxa!
    // https://github.com/CatalogueOfLife/backend/issues/1003
    assertTree("cat0.txt");
    plant = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
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
    NameUsageBase trg = getByName(Datasets.COL, Rank.KINGDOM, "Viruses");
    createSector(Sector.Mode.UNION, src, trg);
    
    syncAll();
    
    assertTree("cat14.txt");
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1150
   */
  @Test
  public void testDipteraUncertainGenus() throws Exception {
    print(datasetKey(24, DataFormat.COLDP));

    NameUsageBase diptera = getByName(datasetKey(24, DataFormat.COLDP), Rank.ORDER, "Diptera");
    NameUsageBase insecta = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, diptera, insecta);

    syncAll();

    assertTree("cat24.txt");
  }

  @Test
  public void testMerge() throws Exception {
    print("cat0.txt");
    //print(datasetKey(0, DataFormat.COLDP));

    NameUsageBase src = getByName(datasetKey(0, DataFormat.COLDP), Rank.KINGDOM, "Plantae");
    final NameUsageBase plant = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.UNION, src, plant);

    syncAll();

    src = getByName(datasetKey(1, DataFormat.DWCA), Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.MERGE, src, plant);

    src = getByName(datasetKey(2, DataFormat.DWCA), Rank.PHYLUM, "Basidiomycota");
    final NameUsageBase basi = getByName(Datasets.COL, Rank.PHYLUM, "Basidiomycota");
    createSector(Sector.Mode.MERGE, src, basi);

    src = getByName(datasetKey(25, DataFormat.COLDP), Rank.FAMILY, "Asteraceae");
    final NameUsageBase asteraceae = getByName(Datasets.COL, Rank.FAMILY, "Asteraceae");
    createSector(Sector.Mode.MERGE, src, asteraceae);

    // do the merges 3 times to make sure internal deletions and the matcher cache work correct
    mergeAndTest(plant);
    mergeAndTest(plant);
    mergeAndTest(plant);
  }

  @Test
  public void mergeCarettas() throws Exception {
    final int srcDatasetKey = datasetKey(26, DataFormat.COLDP);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    var src = getByName(datasetKey(26, DataFormat.COLDP), Rank.FAMILY, "Cheloniidae");
    createSector(Sector.Mode.ATTACH, src, animalia);

    syncAll();
    print(Datasets.COL);

    src = getByName(srcDatasetKey, Rank.FAMILY, "Keloniidae");
    createSector(Sector.Mode.MERGE, src, animalia);

    src = getByName(srcDatasetKey, Rank.FAMILY, "Teeloniidae");
    createSector(Sector.Mode.MERGE, src, animalia);

    src = getByName(srcDatasetKey, Rank.FAMILY, "Pheloniidae");
    createSector(Sector.Mode.MERGE, src, animalia);

    // do the merges 2 times to make sure internal deletions and the matcher cache work correct
    syncMergesOnly();
    print(Datasets.COL);

    var caretta = getByName(Datasets.COL, Rank.SPECIES, "Caretta caretta");
    assertEquals("Caretta caretta", caretta.getName().getScientificName());
    assertEquals("Linnaeus, 1758", caretta.getName().getAuthorship());

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      var vs = session.getMapper(VerbatimSourceMapper.class).getWithSources(caretta);
      assertEquals(srcDatasetKey, (int) vs.getSourceDatasetKey());
      assertEquals("10", vs.getSourceId());
      assertTrue(DSID.equals(DSID.of(srcDatasetKey, "11"), vs.getSecondarySources().get(InfoGroup.AUTHORSHIP)));
      assertTrue(DSID.equals(DSID.of(srcDatasetKey, "13"), vs.getSecondarySources().get(InfoGroup.PUBLISHED_IN)));
      assertEquals(2, vs.getSecondarySources().size());
    }
  }


  @Test
  public void mergeOutsideTarget() throws Exception {
    final int srcDatasetKey = datasetKey(27, DataFormat.COLDP);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    final NameUsageBase plants = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");

    createSector(Sector.Mode.MERGE, srcDatasetKey, null, animalia);

    syncAll();
    print(Datasets.COL);

    // TODO: we have one plant that falls outside the target sector...
  }

  void mergeAndTest(NameUsageBase plant) throws IOException {
    syncMergesOnly();

    final String plantID = plant.getId();
    assertNull(plant.getSectorKey());

    // Paulownia × tomentosa f. pasta is a provisional name and should not create implicit taxa!
    // https://github.com/CatalogueOfLife/backend/issues/1003
    assertTree("cat-merge1.txt");
    var plant2 = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
    // make sure the kingdom is not part of the sector, we merged!
    assertNull(plant2.getSectorKey());
    assertEquals(plantID, plant2.getId());
  }
}