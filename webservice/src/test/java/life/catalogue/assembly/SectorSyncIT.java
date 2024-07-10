package life.catalogue.assembly;

import life.catalogue.TestDataGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.dao.TaxonDao;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.*;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

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
public class SectorSyncIT extends SectorSyncTestBase {
  
  final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");
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


  @Test
  public void accordingto() throws Exception {
    final int srcKey = dataRule.mapKey(DataFormat.COLDP, 14);
    final DSID<String> key = DSID.root(srcKey);
    final String rKey = "r456";
    final String uKey = "Culicidae-Culicinae-Culicini-Culex-americanus-d0be074c0";
    // create one species with according to
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var rm = session.getMapper(ReferenceMapper.class);
      var tm = session.getMapper(TaxonMapper.class);

      var r = new Reference();
      r.setId(rKey);
      r.setDatasetKey(srcKey);
      r.setCitation("Döring (1999): PonTaurus");
      r.applyUser(TestDataRule.TEST_USER);
      rm.create(r);

      var nu = tm.get(DSID.of(srcKey, uKey));
      nu.setAccordingToId(rKey);
      tm.update(nu);
    }

    print(Datasets.COL);
    print(srcKey);

    NameUsageBase src = getByName(srcKey, Rank.ORDER, "Diptera");
    NameUsageBase trg = getByName(Datasets.COL, Rank.CLASS, "Insecta");
    final var sid = createSector(Sector.Mode.ATTACH, src, trg, s -> {
      s.setCopyAccordingTo(false);
      s.setRemoveOrdinals(true);
    });

    syncAll();
    assertTree("cat14b.txt");
    NameUsageBase u = getByName(Datasets.COL, Rank.SPECIES, "Culex americanus");
    assertNull(u.getAccordingToId());
    assertNull(u.getAccordingTo());

    // sync again but this time allow accordingTo
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var ds = dm.getSettings(Datasets.COL);
      ds.put(Setting.SECTOR_COPY_ACCORDING_TO, true);
      dm.updateSettings(Datasets.COL, ds, Users.TESTER);
    }
    syncAll();
    assertTree("cat14b.txt");
    u = getByName(Datasets.COL, Rank.SPECIES, "Culex americanus");
    assertNotNull(u.getAccordingToId());
    assertNotNull(u.getAccordingTo());

    // cleanup dataset which persists for all tests
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var tm = session.getMapper(TaxonMapper.class);
      var nu = tm.get(key.id(uKey));
      nu.setAccordingToId(null);
      tm.update(nu);

      session.getMapper(ReferenceMapper.class).delete(key.id(rKey));
    }
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

    UsageInfo ti = tdao.getUsageInfo(sp);

    Reference r = ti.getReference(sp.getName().getPublishedInId());
    assertEquals(sp.getDatasetKey(), r.getDatasetKey());
    assertEquals(sp.getSectorKey(), r.getSectorKey());
    assertEquals("Greuter,W. et al. (1989). Med-Checklist Vol.4 (Published).", r.getCitation());
    assertEquals(2, ti.getReferences().size());

    Taxon t = getDraftTaxonBySourceID(srcKey1, "13287");
    assertEquals(Datasets.COL, (int) t.getDatasetKey());
    // 19 vernaculars, 5 distinct refs
    ti = tdao.getUsageInfo(t);
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
    createDecision(d4key, simple(src), EditorialDecision.Mode.BLOCK, null, null);
  
    src = getByID(d4key, "11");
    Name newName = new Name();
    newName.setScientificName("Euplectus cavicollis");
    newName.setAuthorship("LeConte, J. L., 1878");
    createDecision(d4key, simple(src), EditorialDecision.Mode.UPDATE, newName, null);
    
    syncAll();
    assertTree("cat4.txt");
  
    NameUsageBase eucav = getByName(Datasets.COL, Rank.SPECIES, "Euplectus cavicollis");
    assertEquals("Euplectus cavicollis", eucav.getName().getScientificName());
    assertEquals("LeConte, J. L., 1878", eucav.getName().getAuthorship());
    assertEquals(NameType.SCIENTIFIC, eucav.getName().getType());
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/593#issuecomment-1833491252
   */
  @Test
  public void syn2accDecision() throws Exception {
    final int dkey = 102;
    print(dkey);

    NameUsageBase subject = getByName(dkey, Rank.FAMILY, "Fabaceae");
    NameUsageBase target = getByName(Datasets.COL, Rank.PHYLUM, "Tracheophyta");
    createSector(Sector.Mode.ATTACH, subject, target);

    NameUsageBase src = getByName(dkey, Rank.SPECIES, "Acacia riparioides");
    createDecision(dkey, simple(src), EditorialDecision.Mode.UPDATE, null, TaxonomicStatus.ACCEPTED);

    src = getByName(dkey, Rank.SUBSPECIES, "Astragalus caprinus subsp. lanigerus");
    createDecision(dkey, simple(src), EditorialDecision.Mode.UPDATE, null, TaxonomicStatus.ACCEPTED);

    src = getByName(dkey, Rank.VARIETY, "Astragalus caprinus var. hirsutus");
    createDecision(dkey, simple(src), EditorialDecision.Mode.UPDATE, null, TaxonomicStatus.ACCEPTED);

    syncAll();
    assertTree("cat1.txt");
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
    NameUsageBase trg = getByName(Datasets.COL, Rank.UNRANKED, "Viruses");
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
    assertEquals(2, stats.getIgnoredByReasonCount().keySet().size());
    assertEquals(7, (int)stats.getIgnoredByReasonCount().get(IgnoreReason.RANK));
    assertEquals(7, (int)stats.getIgnoredByReasonCount().get(IgnoreReason.IGNORED_PARENT));
    var parent = getByName(Datasets.COL, Rank.UNRANKED, "3372");
    assertNull(parent); // we dont sync unranked
    var reticulatus = getByName(Datasets.COL, Rank.SPECIES, "Tenebrio reticulatus");
    assertNull(reticulatus); // we dont sync synonyms without parent
  }

  /**
   * Skip ambiguous name matches.
   */
  @Test
  public void mergeAmbiguous() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.COLDP, 26);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    var src = getByName(srcDatasetKey, Rank.KINGDOM, "Animalia");
    createSector(Sector.Mode.UNION, src, animalia);

    syncAll();
    print(Datasets.COL);
    var carettas = listByName(Datasets.COL, Rank.SPECIES, "Caretta caretta");
    assertEquals(4, carettas.size());

    createSector(Sector.Mode.MERGE, srcDatasetKey, animalia);

    src = getByName(srcDatasetKey, Rank.FAMILY, "Keloniidae");
    createSector(Sector.Mode.MERGE, src, animalia);

    // do the merges 2 times to make sure internal deletions and the matcher cache work correct
    syncMergesOnly();
    print(Datasets.COL);
    syncMergesOnly();
    print(Datasets.COL);

    // still just 4
    carettas = listByName(Datasets.COL, Rank.SPECIES, "Caretta caretta");
    assertEquals(4, carettas.size());
  }

  @Test
  public void mergeOutsideTarget() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.COLDP, 27);
    final NameUsageBase animalia = getByName(Datasets.COL, Rank.KINGDOM, "Animalia");
    createSector(Sector.Mode.MERGE, srcDatasetKey, null, animalia);

    print(Datasets.COL);
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

  @Test
  public void mergeOddNameTypes() throws Exception {
    final int srcDatasetKey = dataRule.mapKey(DataFormat.COLDP, 38);
    print(srcDatasetKey);

    final NameUsageBase plants = getByName(Datasets.COL, Rank.KINGDOM, "Plantae");
    final NameUsageBase asteracea = getByID(srcDatasetKey, "1");
    // base attachment with just major linnean ranks and scientific names
    createSector(Sector.Mode.ATTACH, srcDatasetKey, asteracea, plants, s -> {
      s.setNameTypes(Set.of(NameType.SCIENTIFIC));
      s.setRanks(Set.of(Rank.FAMILY, Rank.GENUS, Rank.SPECIES));
    });
    syncAll();
    print(Datasets.COL);

    final var sid = createSector(Sector.Mode.MERGE, srcDatasetKey, null, plants, s -> {
      s.setNameTypes(Set.of(NameType.SCIENTIFIC, NameType.INFORMAL, NameType.OTU));
      s.setRanks(null);
      s.setCode(NomCode.BOTANICAL);
      disableAutoBlocking(s);
    });
    syncMergesOnly();
    assertTree("cat38.txt");
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