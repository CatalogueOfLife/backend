package life.catalogue.importer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import life.catalogue.api.model.*;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.command.initdb.InitDbCmd;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.model.RankedName;
import life.catalogue.matching.NameIndexFactory;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static life.catalogue.api.TestEntityGenerator.setUserDate;
import static life.catalogue.api.vocab.DataFormat.*;
import static org.junit.Assert.*;

/**
 *
 */
public class PgImportIT {
  
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private DatasetWithSettings dataset;
  private VerbatimRecordMapper vMapper;
  private boolean fullInit = true;
  SynonymDao sdao;
  TaxonDao tdao;
  NameDao ndao;
  ReferenceDao rdao;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  @Before
  public void initCfg() {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    dataset = new DatasetWithSettings();
    dataset.setType(DatasetType.OTHER);
    dataset.setOrigin(DatasetOrigin.EXTERNAL);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    if (fullInit) {
      InitDbCmd.setupColPartition(testDataRule.getSqlSession());
      testDataRule.commit();
    }
  
    sdao = new SynonymDao(PgSetupRule.getSqlSessionFactory());
    ndao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), ndao, NameUsageIndexService.passThru());
    rdao = new ReferenceDao(PgSetupRule.getSqlSessionFactory());
  }
  
  @After
  public void cleanup() {
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
  
  void normalizeAndImport(DataFormat format, int key) throws Exception {
    URL url = getClass().getResource("/" + format.name().toLowerCase() + "/" + key);
    dataset.setDataFormat(format);
    normalizeAndImport(Paths.get(url.toURI()));
  }
  
  void normalizeAndImport(Path source) {
    try {
      // insert trusted dataset
      dataset.setTitle("Test Dataset " + source.toString());
      
      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new key, usually 2000!
      session.getMapper(DatasetMapper.class).createAll(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      Normalizer norm = new Normalizer(dataset, store, source,
        NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started(),
        ImageService.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(1, dataset, store, PgSetupRule.getSqlSessionFactory(), icfg);
      importer.call();
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  void normalizeAndImport(URI url, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalizeAndImport);
  }
  
  void normalizeAndImportArchive(File file, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // decompress
    ExternalSourceUtil.consumeFile(file, this::normalizeAndImport);
  }
  
  void normalizeAndImportFolder(File folder, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // decompress
    normalizeAndImport(folder.toPath());
  }

  void verifyNamesIndexIds(int datasetKey){
    try(SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()){
      NameMapper nm = session.getMapper(NameMapper.class);
      for (Name n : nm.processDataset(datasetKey)) {
        assertNotNull(n.getNameIndexId());
        assertNotNull(n.getNameIndexMatchType());
      }
    }
  }

  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo())
        .generateMetrics(dataset.getKey(), Users.TESTER);
  }


  @Test
  public void testPublishedIn() throws Exception {
    normalizeAndImport(DWCA, 0);

    Name trametes_modesta = ndao.get(key(dataset.getKey(), "324805"));
    
    Reference pubIn = rdao.get(key(dataset.getKey(), trametes_modesta.getPublishedInId()), trametes_modesta.getPublishedInPage());
    assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getCitation());
    assertEquals("r4", pubIn.getId());
  }

  @Test
  public void testDwca1() throws Exception {
    normalizeAndImport(DWCA, 1);

    verifyNamesIndexIds(dataset.getKey());

    // check basionym
    Name n1006 = ndao.get(key(dataset.getKey(), "1006"));
    assertEquals("Leontodon taraxacoides", n1006.getScientificName());
    
    List<NameRelation> rels = ndao.relations(dataset.getKey(), n1006.getId());
    assertEquals(1, rels.size());
    
    Name bas = ndao.getBasionym(key(dataset.getKey(), n1006.getId()));
    assertEquals("Leonida taraxacoida", bas.getScientificName());
    assertEquals(n1006.getHomotypicNameId(), bas.getHomotypicNameId());
    
    // check taxon parents
    assertParents(tdao, "1006", "102", "30", "20", "10", "1");
  }
  
  @Test
  public void testIpniDwca() throws Exception {
    normalizeAndImport(DWCA, 27);
  }
  
  @Test
  public void testDuplicates() throws Exception {
    normalizeAndImport(DWCA, 35);
  }
  
  /**
   * 1->2->1 should be: 1->2
   * <p>
   * 10->11->12->10, 13->11 should be: 10,13->11 12
   */
  @Test
  public void chainedBasionyms() throws Exception {
    normalizeAndImport(DWCA, 28);
    // verify results
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      // check species name
      Name n1 = ndao.get(key(dataset.getKey(), "1"));
      Name n2 = ndao.get(key(dataset.getKey(), "2"));
      
      assertEquals(n2.getHomotypicNameId(), n1.getHomotypicNameId());
      assertTrue(n1.getId().equals(n2.getHomotypicNameId())
          || n2.getId().equals(n2.getHomotypicNameId()));
      assertIssue(n1, Issue.CHAINED_BASIONYM);
      assertIssue(n2, Issue.CHAINED_BASIONYM);
      
      Name n10 = ndao.get(key(dataset.getKey(), "10"));
      Name n11 = ndao.get(key(dataset.getKey(), "11"));
      Name n12 = ndao.get(key(dataset.getKey(), "12"));
      Name n13 = ndao.get(key(dataset.getKey(), "13"));
      
      assertEquals(n10.getId(), n10.getHomotypicNameId());
      assertEquals(n10.getId(), n11.getHomotypicNameId());
      assertEquals(n10.getId(), n13.getHomotypicNameId());
      assertEquals(n12.getId(), n12.getHomotypicNameId());
      
      assertIssue(n10, Issue.CHAINED_BASIONYM);
      assertIssue(n11, Issue.CHAINED_BASIONYM);
      assertIssue(n12, Issue.CHAINED_BASIONYM);
      assertNoIssue(n13, Issue.CHAINED_BASIONYM);
    }
  }
  
  private void assertIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(DSID.vkey(ent));
    assertTrue(v.hasIssue(issue));
  }
  
  private void assertNoIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(DSID.vkey(ent));
    assertFalse(v.hasIssue(issue));
  }
  
  public static Set<Distribution> expectedDwca24Distributions() {
    Set<Distribution> expD = Sets.newHashSet();
    expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
    expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));
    return expD;
  }
  
  @Test
  public void testSupplementary() throws Exception {
    normalizeAndImport(DWCA, 24);
    
    // verify results
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      // check species name
      Taxon tax = tdao.get(key(dataset.getKey(), "1000"));
      assertEquals("Crepis pulchra", tax.getName().getScientificName());
      
      TaxonInfo info = tdao.getTaxonInfo(dataset.getKey(), tax.getId());
      // check vernaculars
      Map<String, String> expV = Maps.newHashMap();
      expV.put("deu", "Schöner Pippau");
      expV.put("eng", "smallflower hawksbeard");
      assertEquals(expV.size(), info.getVernacularNames().size());
      for (VernacularName vn : info.getVernacularNames()) {
        assertEquals(expV.remove(vn.getLanguage()), vn.getName());
        assertNotNull(vn.getVerbatimKey());
      }
      assertTrue(expV.isEmpty());
      
      // check distributions
      Set<Distribution> expD = expectedDwca24Distributions();
      assertEquals(expD.size(), info.getDistributions().size());
      // remove dist keys before we check equality
      info.getDistributions().forEach(d -> {
        assertNotNull(d.getKey());
        assertNotNull(d.getVerbatimKey());
        d.setKey(null);
        d.setVerbatimKey(null);
        setUserDate(d, null, null);
      });
      Set<Distribution> imported = Sets.newHashSet(info.getDistributions());
      
      Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
      for (Distribution d : diff) {
        System.out.println(d);
      }
      assertEquals(expD, imported);
    }
  }
  
  @Test
  public void testAcef0() throws Exception {
    normalizeAndImport(ACEF, 0);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper uMapper = session.getMapper(NameUsageMapper.class);
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      Name n = ndao.get(key(dataset.getKey(), "s7"));
      assertEquals("Astragalus nonexistus", n.getScientificName());
      assertEquals("DC.", n.getAuthorship());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.ACCEPTED_ID_INVALID);
      
      // a bare name
      assertTrue(uMapper.listByNameID(dataset.getKey(), n.getId()).isEmpty());
      assertNull(tdao.get(key(dataset.getKey(), "s7")));
      
      n = ndao.get(key(dataset.getKey(), "s6"));
      assertEquals("Astragalus beersabeensis", n.getScientificName());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.SYNONYM_DATA_MOVED);
      
      List<NameUsageBase> syns = uMapper.listByNameID(dataset.getKey(), n.getId());
      assertEquals(1, syns.size());
      assertTrue(syns.get(0).isSynonym());
      Synonym s = (Synonym) syns.get(0);
      assertEquals("Astracantha arnacantha", s.getAccepted().getName().getScientificName());
      
      TaxonInfo t = tdao.getTaxonInfo(s.getAccepted());
      
      assertEquals(1, t.getVernacularNames().size());
      assertEquals(2, t.getDistributions().size());
      assertEquals(3, t.getReferences().size());
      
      VernacularName v = t.getVernacularNames().get(0);
      assertEquals("Beer bean", v.getName());
      
      // make sure references get indexed during imports
      // https://github.com/Sp2000/colplus-backend/issues/25
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      ReferenceSearchRequest req = ReferenceSearchRequest.byQuery("Canarias");
      List<Reference> out = rm.search(dataset.getKey(), req, new Page());
      assertEquals(1, out.size());
      assertEquals("24", out.get(0).getId());
  
    }
  }
  
  @Test
  public void testAcef1() throws Exception {
    normalizeAndImport(ACEF, 1);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Taxon t = tdao.get(key(dataset.getKey(), "14649"));
      assertEquals("Zapoteca formosa", t.getName().getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.getName().getAuthorship());
      assertEquals(Rank.SPECIES, t.getName().getRank());
      
      TaxonInfo info = tdao.getTaxonInfo(t);
      // distributions
      assertEquals(3, info.getDistributions().size());
      Set<String> areas = Sets.newHashSet("AGE-BA", "BZC-MS", "BZC-MT");
      for (Distribution d : info.getDistributions()) {
        assertEquals(Gazetteer.TDWG, d.getGazetteer());
        assertTrue(areas.remove(d.getArea()));
      }
      
      // vernacular
      assertEquals(3, info.getVernacularNames().size());
      Set<String> names = Sets.newHashSet("Ramkurthi", "Ram Kurthi", "отчество");
      for (VernacularName v : info.getVernacularNames()) {
        assertEquals(v.getName().startsWith("R") ? "hin" : "rus",
            v.getLanguage());
        assertTrue(names.remove(v.getName()));
      }
    }
  }
  
  @Test
  public void testAcef69() throws Exception {
    normalizeAndImport(ACEF, 69);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonMapper taxMapper = session.getMapper(TaxonMapper.class);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      
      Taxon t = tdao.get(key(dataset.getKey(), "Rho-144"));
      assertEquals("Afrogamasellus lokelei Daele, 1976", t.getName().getLabel());
      
      List<Taxon> classific = taxMapper.classification(t);
      LinkedList<RankedName> expected =
          Lists.newLinkedList(Lists.newArrayList(rn(Rank.KINGDOM, "Animalia"),
              rn(Rank.PHYLUM, "Arthropoda"), rn(Rank.CLASS, "Arachnida"),
              rn(Rank.ORDER, "Mesostigmata"), rn(Rank.SUPERFAMILY, "Rhodacaroidea"),
              rn(Rank.FAMILY, "Rhodacaridae"), rn(Rank.GENUS, "Afrogamasellus")));
      
      assertEquals(expected.size(), classific.size());
      for (Taxon ht : classific) {
        RankedName expect = expected.removeLast();
        assertEquals(expect.rank, ht.getName().getRank());
        assertEquals(expect.name, ht.getName().getLabel());
      }
      
      assertEquals(TaxonomicStatus.ACCEPTED, t.getStatus());
      assertEquals("Tester", t.getScrutinizer());
      assertEquals("2008", t.getScrutinizerDate().toString());
      assertFalse(t.isExtinct());
      assertTrue(t.getEnvironments().isEmpty());
      assertNull(t.getRemarks());
      assertNull(t.getLink());
      
      // test synonym
      Name sn = ndao.get(key(dataset.getKey(), "Rho-140"));
      assertEquals("Rhodacarus guevarai Guevara-Benitez, 1974", sn.getLabel());
      
      // in acef name & taxon ids are the same
      Synonym syn = synMapper.get(key(dataset.getKey(), sn.getId()));
      assertNotNull(syn);
      
      t = tdao.get(key(dataset.getKey(), "Rho-61"));
      assertEquals("Multidentorhodacarus denticulatus (Berlese, 1920)", t.getName().getLabel());
      assertEquals(t, syn.getAccepted());
    }
  }
  
  @Test
  public void testAcef6Misapplied() throws Exception {
    normalizeAndImport(ACEF, 6);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
  
      Taxon t = tdao.get(key(dataset.getKey(), "MD2"));
      assertEquals("Latrodectus mactans", t.getName().getScientificName());
      assertEquals("(Fabricius, 1775)", t.getName().getAuthorship());
  
      TaxonInfo info = tdao.getTaxonInfo(t);
      // Walckenaer1805;Walckenaer, CA;1805;Table of the aranid or essential characters of the
      // tribes, genera, families and races contained in the genus Aranea of ​​Linnaeus, with the
      // designation of the species included in each of these divisions . Paris, 88 pp;;
      Reference pubIn = info.getReference(t.getName().getPublishedInId());
      assertEquals("Walckenaer1805", pubIn.getId());
      // we cannot test this as our CslParserMock only populates the title...
      // assertEquals(1805, (int) pubIn.getYear());
      // assertEquals("Walckenaer, CA 1805. Table of the aranid or essential characters of the
      // tribes, genera, families and races contained in the genus Aranea of \u200B\u200BLinnaeus,
      // with the designation of the species included in each of these divisions . Paris, 88 pp",
      // pubIn.getCsl().getTitle());
      
      assertEquals(4, info.getReferences().size());
      for (String refId : info.getTaxon().getReferenceIds()) {
        Reference r = info.getReference(refId);
        assertNotNull(r);
      }
      
      Synonymy syn = tdao.getSynonymy(t);
      assertEquals(5, syn.size());
      assertEquals(2, syn.getMisapplied().size());
      assertEquals(3, syn.getHeterotypic().size());
      assertEquals(0, syn.getHomotypic().size());
      
      Synonym s = sm.get(key(dataset.getKey(), "s5"));
      // assertEquals("auct. Whittaker 1981", s.getAccordingTo());
      assertEquals(TaxonomicStatus.MISAPPLIED, s.getStatus());
    }
  }
  
  /**
   * Homotypic keys and basionym acts.
   */
  @Test
  public void testDwca29() throws Exception {
    normalizeAndImport(DWCA, 29);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Taxon annua = tdao.get(key(dataset.getKey(), "4"));
      assertEquals("Poa annua L.", annua.getName().getLabel());
      
      TaxonInfo info = tdao.getTaxonInfo(annua);
      Reference pubIn = info.getReference(annua.getName().getPublishedInId());
      assertEquals("Sp. Pl. 1: 68 (1753).", pubIn.getCitation());
      
      Synonymy syn = tdao.getSynonymy(annua);
      assertEquals(4, syn.size());
      assertEquals(0, syn.getMisapplied().size());
      assertEquals(2, syn.getHeterotypic().size());
      assertEquals(1, syn.getHomotypic().size());
      
      for (Name n : syn.getHomotypic()) {
        assertEquals(annua.getName().getHomotypicNameId(), n.getHomotypicNameId());
      }
      for (List<Name> group : syn.getHeterotypic()) {
        String homoKey = group.get(0).getHomotypicNameId();
        assertNotEquals(homoKey, annua.getName().getHomotypicNameId());
        for (Name n : group) {
          assertEquals(homoKey, n.getHomotypicNameId());
        }
      }
      
      NameRelationMapper actMapper = session.getMapper(NameRelationMapper.class);
      // Poa annua has not explicitly declared a basionym
      assertTrue(actMapper.list(dataset.getKey(), annua.getName().getId()).isEmpty());
      
      Name reptans1 = ndao.get(key(dataset.getKey(), "7"));
      Name reptans2 = ndao.get(key(dataset.getKey(), "8"));
      assertEquals(1, actMapper.list(dataset.getKey(), reptans1.getId()).size());
      assertEquals(1, actMapper.list(dataset.getKey(), reptans2.getId()).size());
      
      NameRelation act = actMapper.list(dataset.getKey(), reptans1.getId()).get(0);
      assertEquals(NomRelType.BASIONYM, act.getType());
      assertEquals(reptans1.getId(), act.getNameId());
      assertEquals(reptans2.getId(), act.getRelatedNameId());
    }
  }
  
  /**
   * duplicate scientificNameID s should not bring down the importer
   */
  @Test
  public void testSwampsSciNameIDs() throws Exception {
    normalizeAndImport(DWCA, 33);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Name n = ndao.get(key(dataset.getKey(), "30405"));
      assertEquals("Haematomma ochroleucum var. porphyrium", n.getScientificName());
      assertEquals("30405", n.getId());
      
      // this is buggy normalization of bad data - should really be just one...
      assertEquals(2, tdao.listRoot(dataset.getKey(), new Page()).getResult().size());
    }
  }
  
  @Test
  public void testVascanProparte() throws Exception {
    normalizeAndImport(DWCA, 36);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      Name n = ndao.get(key(dataset.getKey(), "9946"));
  
      List<NameUsageBase> usages = num.listByNameID(n.getDatasetKey(), n.getId());
      assertEquals(2, usages.size());
      for (NameUsageBase u : usages) {
        Synonym s = (Synonym) u;
        assertTrue(s.getId().startsWith("9946"));
      }
      assertEquals(usages.get(0).getName(), usages.get(1).getName());
      assertNotEquals(usages.get(0).getId(), usages.get(1).getId());
    }
  }
  
  @Test
  public void testColdpSpecs() throws Exception {
    normalizeAndImport(COLDP, 0);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Name n = ndao.get(key(dataset.getKey(), "1000"));
      assertEquals("Platycarpha glomerata", n.getScientificName());
      assertEquals("(Thunberg) A. P. de Candolle", n.getAuthorship());
      assertEquals("1000", n.getId());
      assertEquals(Rank.SPECIES, n.getRank());
    
      // one root
      assertEquals(1, tdao.listRoot(dataset.getKey(), new Page()).getResult().size());
  
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      List<NameUsageBase> syns = um.listByNameID(dataset.getKey(), "1006-s3");
      assertEquals(1, syns.size());
      Synonym s3 = (Synonym) syns.get(0);
      assertEquals("Leonida taraxacoida Vill.", s3.getName().getLabel());
      assertEquals("1006", s3.getAccepted().getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", s3.getAccepted().getName().getLabel());
      
      // https://github.com/Sp2000/colplus-backend/issues/237
      VerbatimRecordMapper vm = session.getMapper(VerbatimRecordMapper.class);
      for (VerbatimRecord v : vm.list(dataset.getKey(), null, null, LogicalOperator.AND, null, null, new Page(0, 100))) {
        for (Term t : v.terms()) {
          assertFalse(t.qualifiedName(), t instanceof UnknownTerm);
        }
      }

      // types
      TypeMaterialMapper tmm = session.getMapper(TypeMaterialMapper.class);
      List<TypeMaterial> types = tmm.listByName(DSID.of(dataset.getKey(), "1001c"));
      assertEquals(2, types.size());
      for (TypeMaterial tm : types) {
        assertEquals("1001c", tm.getNameId());
        assertEquals(dataset.getKey(), tm.getDatasetKey());
        assertNull(tm.getSectorKey());
        assertNotNull(tm.getCitation());
      }

      // treatments
      TreatmentMapper trm = session.getMapper(TreatmentMapper.class);
      Treatment t = trm.get(DSID.of(dataset.getKey(), "Jarvis2007"));
      assertEquals(TreatmentFormat.HTML, t.getFormat());
      assertTrue(t.getDocument().startsWith("<div>"));
      assertEquals("Jarvis2007", t.getId());
    }
  
    DatasetImport di = metrics();
    assertEquals(2, (int) metrics().getTreatmentCount());
    assertEquals(9, (int) di.getDistributionCount());
    assertEquals(1, (int) di.getMediaCount());
    assertEquals(9, (int) di.getReferenceCount());
    assertEquals(2, (int) di.getVernacularCount());
    assertEquals(23, (int) di.getTaxonCount());
    assertEquals(3, (int) di.getTypeMaterialCount());
    assertEquals(28, (int) di.getNameCount());
    assertEquals(89, (int) di.getVerbatimCount());
    
    //assertFalse(metrics().getIssuesCount().containsKey(Issue.PARENT_ID_INVALID));
    assertEquals(5, (int) di.getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));
    // 1 provisional status taxon
    assertEquals((int) metrics().getTaxonCount(), 1 + di.getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals(1, (int) di.getMediaByTypeCount().get(MediaType.IMAGE));
    assertEquals(2, (int) di.getNamesByRankCount().get(Rank.FAMILY));
    assertEquals(4, (int) di.getNamesByRankCount().get(Rank.GENUS));
    assertEquals(12, (int) di.getNamesByRankCount().get(Rank.SPECIES));
    assertEquals(3, (int) di.getNamesByRankCount().get(Rank.SUBSPECIES));
    assertEquals(9, (int) di.getDistributionsByGazetteerCount().get(Gazetteer.ISO));
    assertEquals(2, (int) di.getTypeMaterialByStatusCount().get(TypeStatus.HOLOTYPE));
  }
  
  /**
   * Rich FishBase extract
   * https://github.com/Sp2000/ConversionTool/issues/6
   */
  @Test
  public void acefSynonymRefs() throws Exception {
    normalizeAndImport(ACEF, 18);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Taxon t  = tdao.get(key(dataset.getKey(), "48827"));
      assertEquals(2, t.getReferenceIds().size());
  
      Synonym s  = sdao.get(key(dataset.getKey(), "146661"));
      assertEquals(t.getId(), s.getParentId());
      assertEquals(TaxonomicStatus.AMBIGUOUS_SYNONYM, s.getStatus());
      assertEquals(1, s.getReferenceIds().size());
      assertEquals("10418", s.getReferenceIds().get(0));
      //TODO: check for synonym references !!!
    }
  }
  
  /**
   * Test with 3 synonyms having the same accepted
   * https://github.com/Sp2000/colplus-backend/issues/461
   */
  @Test
  public void acefAmbiguousSyns() throws Exception {
    normalizeAndImport(ACEF, 20);
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/477
   */
  @Test
  public void acefSuperfam() throws Exception {
    normalizeAndImport(ACEF, 21);
  }
  
  @Test
  @Ignore("manual test for debugging entire imports")
  public void testExternalManually() throws Exception {
    dataset.setCode(NomCode.ZOOLOGICAL);
    dataset.setType(DatasetType.TAXONOMIC);
  
    //normalizeAndImport(URI.create("https://github.com/Sp2000/coldp/archive/master.zip"), COLDP);
    //normalizeAndImport(URI.create("https://github.com/mdoering/data-ina/archive/master.zip"), COLDP);
    //normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/162.tar.gz"), ACEF);
    normalizeAndImportArchive(new File("/Users/markus/Dropbox/data/checklists/Lepidoptera/Alucitoidea-1.0.zip"), COLDP);
  
    //normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/higher-classification.dwca.zip"), DWCA);
    //normalizeAndImportFolder(new File("/Users/markus/code/col+/data-staphbase/coldp"), COLDP);
    //normalizeAndImport(URI.create("https://plutof.ut.ee/ipt/archive.do?r=unite_sh"), DataFormat.DWCA);
    //normalizeAndImportArchive(new File("/home/ayco/git-repos/colplus-repo/DWCA/itis_global.zip"), DWCA);
    //normalizeAndImportArchive(new File("/Users/markus/Downloads/data-ina-master.zip"), COLDP);
    
  }
  
  private static RankedName rn(Rank rank, String name) {
    return new RankedName(null, name, null, rank);
  }
  
  private static Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    d.setStatus(status);
    return d;
  }
  
  private void assertParents(TaxonDao tdao, String taxonID, String... parentIds) {
    final LinkedList<String> expected = new LinkedList<String>(Arrays.asList(parentIds));
    Taxon t = tdao.get(key(dataset.getKey(), taxonID));
    while (t.getParentId() != null) {
      Taxon parent = tdao.get(key(dataset.getKey(), t.getParentId()));
      assertEquals(expected.pop(), parent.getId());
      t = parent;
    }
    assertTrue(expected.isEmpty());
  }
  
  public static DSID<String> key(int datasetKey, String id) {
    return new DSIDValue<>(datasetKey, id);
  }
  
}
