package life.catalogue.assembly;

import life.catalogue.TestDataGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.*;
import life.catalogue.db.mapper.*;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.validation.constraints.Null;

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
  
  final static SqlSessionFactoryRule pg = new PgSetupRule(); //new PgConnectionRule("col", "postgres", "postgres");
  final static TestDataRule dataRule = TestDataGenerator.syncs();
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

  TaxonDao tdao;
  TestDataRule draftRule;


  @Before
  public void init () throws IOException, SQLException {
    // reset draft
    draftRule = TestDataRule.draft();
    draftRule.initSession();
    draftRule.truncateDraft();
    draftRule.loadData();
    // rematch draft
    matchingRule.rematch(draftRule.testData.key);
    tdao = syncFactoryRule.getTdao();
  }

  @After
  public void after () throws IOException, SQLException {
    draftRule.getSqlSession().close();
  }

  public static NameUsageBase getByName(int datasetKey, Rank rank, String name) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      List<NameUsageBase> taxa = session.getMapper(NameUsageMapper.class).listByName(datasetKey, name, rank, new Page(0,100));
      if (taxa.isEmpty()) return null;
      if (taxa.size() > 1) throw new IllegalStateException("Multiple taxa found for name="+name);
      return taxa.get(0);
    }
  }

  public static List<SimpleName> getClassification(DSID<String> taxon) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).classificationSimple(taxon);
    }
  }

  public static VerbatimSource getSource(DSID<String> key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      return session.getMapper(VerbatimSourceMapper.class).get(key);
    }
  }
  
  NameUsageBase getByID(String id) {
    return getByID(Datasets.COL, id);
  }
  
  NameUsageBase getByID(int datasetKey, String id) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(TaxonMapper.class).get(DSID.of(datasetKey, id));
    }
  }

  Taxon getDraftTaxonBySourceID(int sourceDatasetKey, String id) {
    Taxon src;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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

  public static DSID<Integer> createSector(Sector.Mode mode, NameUsageBase src, NameUsageBase target, Consumer<Sector> modifier) {
    return createSector(mode, null, src.getDatasetKey(), simple(src), simple(target), modifier);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, NameUsageBase src, NameUsageBase target) {
    return createSector(mode, datasetKey, simple(src), simple(target));
  }
  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, NameUsageBase src, NameUsageBase target, Consumer<Sector> modifier) {
    return createSector(mode, null, datasetKey, simple(src), simple(target), modifier);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, int datasetKey, SimpleNameLink src, SimpleNameLink target) {
    return createSector(mode, null, datasetKey, src, target, null);
  }

  public static DSID<Integer> createSector(Sector.Mode mode, Integer priority, int datasetKey, SimpleNameLink src, SimpleNameLink target, Consumer<Sector> modifier) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Sector sector = new Sector();
      sector.setMode(mode);
      sector.setPriority(priority);
      sector.setDatasetKey(Datasets.COL);
      sector.setSubjectDatasetKey(datasetKey);
      sector.setSubject(src);
      sector.setTarget(target);
      sector.setEntities(Set.of(EntityType.values()));
      sector.setNameTypes(Set.of(NameType.SCIENTIFIC, NameType.VIRUS, NameType.HYBRID_FORMULA));
      sector.applyUser(TestDataRule.TEST_USER);
      if (modifier != null) {
        modifier.accept(sector);
      }
      session.getMapper(SectorMapper.class).create(sector);
      return sector;
    }
  }

  public static EditorialDecision createDecision(int datasetKey, SimpleNameLink src, EditorialDecision.Mode mode, Name name) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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

  public static List<SectorImport> syncAll(@Nullable Predicate<Sector> filter) {
    List<SectorImport> imports = new ArrayList<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      for (Sector s : session.getMapper(SectorMapper.class).list(Datasets.COL, null)) {
        if (filter == null || filter.test(s)) {
          imports.add(sync(s));
        }
      }
    }
    return imports;
  }

  /**
   * Syncs into the project
   */
  public static SectorImport sync(Sector s) {
    SectorSync ss = SyncFactoryRule.getFactory().project(s, SectorSyncTest::successCallBack, SectorSyncTest::errorCallBack, TestDataRule.TEST_USER);
    return runSync(ss);
  }

  private static SectorImport runSync(SectorSync ss) {
    System.out.println("\n*** SECTOR " + ss.sector.getMode() + " SYNC " + ss.sectorKey + " ***");
    ss.run();
    if (ss.getState().getState() != ImportState.FINISHED){
      throw new IllegalStateException("SectorSync failed with error: " + ss.getState().getError());
    }
    return ss.getState();
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
    PrinterFactory.dataset(TextTreePrinter.class, datasetKey, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
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
    assertTree(Datasets.COL, openResourceStream(filename));
  }

  public static void assertTree(int datasetKey, InputStream expectedTree) throws IOException {
    assertTree(datasetKey, null, expectedTree);
  }
  public static void assertTree(int datasetKey, @Nullable String rootID, InputStream expectedTree) throws IOException {
    String expected = UTF8IoUtils.readString(expectedTree).trim();
    
    Writer writer = new StringWriter();
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey, rootID);
    PrinterFactory.dataset(TextTreePrinter.class, ttp, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
    
    // compare trees
    System.out.println("\n*** DATASET "+datasetKey+" TREE ***");
    System.out.println(tree);
    assertEquals("Tree not as expected for dataset " + datasetKey, expected, tree);
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
    final int srcKey = dataRule.mapKey(DataFormat.COLDP, 14);
    print(Datasets.COL);
    print(srcKey);

    NameUsageBase src = getByName(srcKey, Rank.ORDER, "Diptera");
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
    final int srcKey = dataRule.mapKey(DataFormat.COLDP, 22);
    print(Datasets.COL);
    print(srcKey);

    NameUsageBase src = getByName(srcKey, Rank.FAMILY, "Acoraceae");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat22.txt");
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1230
   */
  @Test
  public void subgenera() throws Exception {
    final int srcKey1 = dataRule.mapKey(DataFormat.COLDP, 34);
    print(Datasets.COL);
    print(srcKey1);

    NameUsageBase src = getByName(srcKey1, Rank.ORDER, "Coleoptera");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, src, trg);

    syncAll();
    assertTree("cat34.txt");

    final int srcKey2 = dataRule.mapKey(DataFormat.COLDP, 35);
    print(srcKey2);
    src = getByName(srcKey2, Rank.FAMILY, "Chrysomelidae");
    trg = getByName(Datasets.COL, Rank.FAMILY, "Chrysomelidae");
    createSector(Sector.Mode.MERGE, src, trg, s -> {
      s.setRanks(Set.of(Rank.FAMILY, Rank.GENUS, Rank.SUBGENUS, Rank.SPECIES, Rank.SUBSPECIES));
    });

    syncAll();
    assertTree("cat34-35.txt");
  }

  @Test
  public void test1_5_6() throws Exception {
    final int srcKey1 = dataRule.mapKey(DataFormat.ACEF, 1);
    final int srcKey2 = dataRule.mapKey(DataFormat.ACEF, 5);
    final int srcKey3 = dataRule.mapKey(DataFormat.ACEF, 6);
    print(Datasets.COL);
    print(srcKey1);
    print(srcKey2);
    print(srcKey3);
  
    NameUsageBase src = getByName(srcKey1, Rank.ORDER, "Fabales");
    NameUsageBase trg = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    DSID<Integer> s1 = DSID.copy(createSector(Sector.Mode.ATTACH, src, trg));
  
    src = getByName(srcKey2, Rank.CLASS, "Insecta");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.UNION, src, trg);
  
    src = getByName(srcKey3, Rank.FAMILY, "Theridiidae");
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

    Taxon t = getDraftTaxonBySourceID(srcKey1, "13287");
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
    final int d4key = dataRule.mapKey(DataFormat.COLDP, 4);
    print(Datasets.COL);
    print(d4key);

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
    final int d1key = dataRule.mapKey(DataFormat.ACEF, 6);
    final int d2key = dataRule.mapKey(DataFormat.ACEF, 11);
    print(Datasets.COL);
    print(d1key);
    print(d2key);
    
    NameUsageBase src = getByName(d1key, Rank.FAMILY, "Theridiidae");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s1 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(d2key, Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s2 = DSID.copy(createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    ));
  
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
    final int d1key = dataRule.mapKey(DataFormat.ACEF, 5);
    final int d2key = dataRule.mapKey(DataFormat.ACEF, 6);
    final int d3key = dataRule.mapKey(DataFormat.ACEF, 11);
    print(Datasets.COL);
    print(d1key);
    print(d2key);
    print(d3key);
  
    NameUsageBase src = getByName(d1key, Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s5 = createSector(Sector.Mode.UNION, src, trg);

    src = getByName(d2key, Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s6 = createSector(Sector.Mode.ATTACH, src, trg);
    
    src = getByName(d3key, Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s11 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    );
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
    final int d1key = dataRule.mapKey(DataFormat.ACEF, 5);
    final int d2key = dataRule.mapKey(DataFormat.ACEF, 6);
    final int d3key = dataRule.mapKey(DataFormat.ACEF, 11);
    print(Datasets.COL);
    print(d1key);
    print(d2key);
    print(d3key);

    NameUsageBase src = getByName(d1key, Rank.CLASS, "Insecta");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s5 = createSector(Sector.Mode.UNION, src, trg);

    src = getByName(d2key, Rank.FAMILY, "Theridiidae");
    trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final DSID<Integer> s6 = createSector(Sector.Mode.ATTACH, src, trg);

    src = getByName(d3key, Rank.GENUS, "Dectus");
    // target without id so far
    final DSID<Integer> s11 = createSector(Sector.Mode.ATTACH, src.getDatasetKey(), simple(src),
      SimpleNameLink.of("Theridiidae", Rank.FAMILY)
    );

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
    final int d1key = dataRule.mapKey(DataFormat.COLDP, 0);
    final int d2key = dataRule.mapKey(DataFormat.COLDP, 2);
    print(Datasets.COL);
    print(d1key);
    print(d2key);
    
    NameUsageBase asteraceae   = getByName(d1key, Rank.FAMILY, "Asteraceae");
    NameUsageBase tracheophyta = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, asteraceae, tracheophyta);
  
    NameUsageBase coleoptera = getByName(d2key, Rank.ORDER, "Coleoptera");
    NameUsageBase insecta = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, coleoptera, insecta);
    
    syncAll();
    assertTree("cat0_2.txt");
  }
  
  @Test
  public void testKingdomSectorAndTypeMaterial() throws Exception {
    final int d1key = dataRule.mapKey(DataFormat.COLDP, 0);
    print(Datasets.COL);
    print(d1key);
    
    NameUsageBase src = getByName(d1key, Rank.KINGDOM, "Plantae");
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

    var crepisbakeri = getByName(Datasets.COL, Rank.SPECIES, "Crepis bakeri");
    // make sure the kingdom is not part of the sector, we merged!
    assertNotNull(crepisbakeri.getSectorKey());

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var typeMapper = session.getMapper(TypeMaterialMapper.class);
      var types = typeMapper.listByName(crepisbakeri.getName());
      assertEquals(1, types.size());
    }

  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/452
   */
  @Test
  public void testVirus() throws Exception {
    final int d1key = dataRule.mapKey(DataFormat.ACEF, 14);
    print(d1key);
    
    NameUsageBase src = getByName(d1key, Rank.KINGDOM, "Viruses");
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
    final int d1key = dataRule.mapKey(DataFormat.COLDP, 24);
    print(d1key);

    NameUsageBase diptera = getByName(d1key, Rank.ORDER, "Diptera");
    NameUsageBase insecta = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    createSector(Sector.Mode.ATTACH, diptera, insecta);

    syncAll();

    assertTree("cat24.txt");
  }

  @Test
  @Ignore("Work in progress")
  public void testMerge() throws Exception {
    final int d1key = dataRule.mapKey(DataFormat.COLDP, 0);
    final int d2key = dataRule.mapKey(DataFormat.DWCA, 1);
    final int d3key = dataRule.mapKey(DataFormat.DWCA, 2);
    final int d4key = dataRule.mapKey(DataFormat.COLDP, 25);
    print("cat0.txt");

    NameUsageBase src = getByName(d1key, Rank.KINGDOM, "Plantae");
    final NameUsageBase plant = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.UNION, src, plant);

    syncAll();

    src = getByName(d2key, Rank.KINGDOM, "Plantae");
    createSector(Sector.Mode.MERGE, src, plant);

    src = getByName(d3key, Rank.PHYLUM, "Basidiomycota");
    final NameUsageBase basi = getByName(Datasets.COL, Rank.PHYLUM, "Basidiomycota");
    createSector(Sector.Mode.MERGE, src, basi);

    src = getByName(d4key, Rank.FAMILY, "Asteraceae");
    final NameUsageBase asteraceae = getByName(Datasets.COL, Rank.FAMILY, "Asteraceae");
    createSector(Sector.Mode.MERGE, src, asteraceae);

    // do the merges 3 times to make sure internal deletions and the matcher cache work correct
    mergeAndTest(plant);
    mergeAndTest(plant);
    mergeAndTest(plant);
  }

  @Test
  public void mergeCarettas() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.COLDP, 26);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    var src = getByName(srcDatasetKey, Rank.FAMILY, "Cheloniidae");
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

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var vs = session.getMapper(VerbatimSourceMapper.class).getWithSources(caretta);
      assertEquals(srcDatasetKey, (int) vs.getSourceDatasetKey());
      assertEquals("10", vs.getSourceId());
      assertTrue(DSID.equals(DSID.of(srcDatasetKey, "11"), vs.getSecondarySources().get(InfoGroup.AUTHORSHIP)));
      assertTrue(DSID.equals(DSID.of(srcDatasetKey, "13"), vs.getSecondarySources().get(InfoGroup.PUBLISHED_IN)));
      assertEquals(2, vs.getSecondarySources().size());
    }
  }

  /**
   * Skip synonyms with a bad accepted name in merge syncs
   */
  @Test
  public void mergeBadSynonym() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.DWCA, 45);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    final var secID = createSector(Sector.Mode.MERGE, srcDatasetKey, null, animalia, s -> {
      s.setNameTypes(null);
    });
    Sector s = sector(secID);

    var stats = sync(s);
    print(Datasets.COL);

    // by default we include all names
    assertFalse(stats.getIgnoredByReasonCount().containsKey(IgnoreReason.NAME_NO_NAME));
    var parent = getByName(Datasets.COL, Rank.UNRANKED, "3372");
    var reticulatus = getByName(Datasets.COL, Rank.SPECIES, "Tenebrio reticulatus");
    assertNotNull(parent);
    assertNotNull(reticulatus);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var sm = session.getMapper(SectorMapper.class);
      s = sm.get(secID);
      s.setNameTypes(Set.of(NameType.SCIENTIFIC)); // only sync proper scientific names
      sm.update(s);
    }

    stats = sync(s);
    print(Datasets.COL);

    // synonym with a bad accepted name should be included if we allow no name types
    assertTrue(stats.getIgnoredByReasonCount().containsKey(IgnoreReason.IGNORED_PARENT));
    assertTrue(stats.getIgnoredByReasonCount().containsKey(IgnoreReason.NAME_OTU)); // 3372 gets declared OTU apparently :(
    parent = getByName(Datasets.COL, Rank.UNRANKED, "3372");
    reticulatus = getByName(Datasets.COL, Rank.SPECIES, "Tenebrio reticulatus");
    assertNull(parent);
    assertNull(reticulatus);
  }

  @Test
  public void mergeOutsideTarget() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.COLDP, 27);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    createSector(Sector.Mode.MERGE, srcDatasetKey, null, animalia);

    syncAll();
    print(Datasets.COL);

    // we have one plant that falls outside the target Animalia sector:
    // 20	Plantae	Tracheophyta	Apiaceae		species	Oenanthe aquatica	(L.) Poir.
    var oa = getByName(Datasets.COL, Rank.SPECIES, "Oenanthe aquatica");
    Classification cl = new Classification(getClassification(oa));
    assertEquals("Plantae", cl.getKingdom());
    var v = getSource(oa);
    assertTrue(v.hasIssue(Issue.SYNC_OUTSIDE_TARGET));
  }

  Sector sector(DSID<Integer> key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return session.getMapper(SectorMapper.class).get(key);
    }
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