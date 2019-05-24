package org.col.importer;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.command.initdb.InitDbCmd;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.config.ImporterConfig;
import org.col.config.NormalizerConfig;
import org.col.dao.*;
import org.col.db.PgSetupRule;
import org.col.db.mapper.*;
import org.col.img.ImageService;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbFactory;
import org.col.importer.neo.model.RankedName;
import org.col.matching.NameIndexFactory;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import static org.col.api.TestEntityGenerator.setUserDate;
import static org.col.api.vocab.DataFormat.*;
import static org.junit.Assert.*;

/**
 *
 */
public class PgImportIT {
  
  private static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;
  private VerbatimRecordMapper vMapper;
  private boolean fullInit = true;
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
    dataset = new Dataset();
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    if (fullInit) {
      InitDbCmd.setupStandardPartitions(testDataRule.getSqlSession());
      testDataRule.commit();
    }
    
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory());
    ndao = new NameDao(PgSetupRule.getSqlSessionFactory(), aNormalizer);
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
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      store.put(dataset);
      Normalizer norm = new Normalizer(store, source, NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer), ImageService.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), aNormalizer, icfg);
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

  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo())
        .generateMetrics(dataset.getKey());
  }
  
  
  
  @Test
  public void testPublishedIn() throws Exception {
    normalizeAndImport(DWCA, 0);

    Name trametes_modesta = ndao.get(dataset.getKey(), "324805");
    
    Reference pubIn = rdao.get(dataset.getKey(), trametes_modesta.getPublishedInId(), trametes_modesta.getPublishedInPage());
    assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getCitation());
    assertEquals(".neodb.aW6r", pubIn.getId());
  }
  
  @Test
  public void testDwca1() throws Exception {
    normalizeAndImport(DWCA, 1);
    
    // check basionym
    Name n1006 = ndao.get(dataset.getKey(), "1006");
    assertEquals("Leontodon taraxacoides", n1006.getScientificName());
    
    List<NameRelation> rels = ndao.relations(dataset.getKey(), n1006.getId());
    assertEquals(1, rels.size());
    
    Name bas = ndao.getBasionym(dataset.getKey(), n1006.getId());
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
      Name n1 = ndao.get(dataset.getKey(), "1");
      Name n2 = ndao.get(dataset.getKey(), "2");
      
      assertEquals(n2.getHomotypicNameId(), n1.getHomotypicNameId());
      assertTrue(n1.getId().equals(n2.getHomotypicNameId())
          || n2.getId().equals(n2.getHomotypicNameId()));
      assertIssue(n1, Issue.CHAINED_BASIONYM);
      assertIssue(n2, Issue.CHAINED_BASIONYM);
      
      Name n10 = ndao.get(dataset.getKey(), "10");
      Name n11 = ndao.get(dataset.getKey(), "11");
      Name n12 = ndao.get(dataset.getKey(), "12");
      Name n13 = ndao.get(dataset.getKey(), "13");
      
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
    VerbatimRecord v = vMapper.get(dataset.getKey(), ent.getVerbatimKey());
    assertTrue(v.hasIssue(issue));
  }
  
  private void assertNoIssue(VerbatimEntity ent, Issue issue) {
    VerbatimRecord v = vMapper.get(dataset.getKey(), ent.getVerbatimKey());
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
    expD.add(dist(Gazetteer.TEXT, "tdwg:cpp; tdwg:of; tdwg:nam", DistributionStatus.NATIVE));
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
      Taxon tax = tdao.get(dataset.getKey(), "1000");
      assertEquals("Crepis pulchra", tax.getName().getScientificName());
      
      TaxonInfo info = tdao.getTaxonInfo(dataset.getKey(), tax.getId());
      // check vernaculars
      Map<Language, String> expV = Maps.newHashMap();
      expV.put(Language.GERMAN, "Schöner Pippau");
      expV.put(Language.ENGLISH, "smallflower hawksbeard");
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
      
      Name n = ndao.get(dataset.getKey(), "s7");
      assertEquals("Astragalus nonexistus", n.getScientificName());
      assertEquals("DC.", n.authorshipComplete());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.ACCEPTED_ID_INVALID);
      
      // a bare name
      assertTrue(uMapper.listByNameID(dataset.getKey(), n.getId()).isEmpty());
      assertNull(tdao.get(dataset.getKey(), "s7"));
      
      n = ndao.get(dataset.getKey(), "s6");
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
      assertEquals(2, t.getTaxonReferences().size());
      
      VernacularName v = t.getVernacularNames().get(0);
      assertEquals("Beer bean", v.getName());
    }
  }
  
  @Test
  public void testAcef1() throws Exception {
    normalizeAndImport(ACEF, 1);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      Taxon t = tdao.get(dataset.getKey(), "14649");
      assertEquals("Zapoteca formosa", t.getName().getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.getName().authorshipComplete());
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
        assertEquals(v.getName().startsWith("R") ? Language.HINDI : Language.RUSSIAN,
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
      
      Taxon t = tdao.get(dataset.getKey(), "Rho-144");
      assertEquals("Afrogamasellus lokelei Daele, 1976", t.getName().canonicalNameComplete());
      
      List<Taxon> classific = taxMapper.classification(t.getDatasetKey(), t.getId());
      LinkedList<RankedName> expected =
          Lists.newLinkedList(Lists.newArrayList(rn(Rank.KINGDOM, "Animalia"),
              rn(Rank.PHYLUM, "Arthropoda"), rn(Rank.CLASS, "Arachnida"),
              rn(Rank.ORDER, "Mesostigmata"), rn(Rank.SUPERFAMILY, "Rhodacaroidea"),
              rn(Rank.FAMILY, "Rhodacaridae"), rn(Rank.GENUS, "Afrogamasellus")));
      
      assertEquals(expected.size(), classific.size());
      for (Taxon ht : classific) {
        RankedName expect = expected.removeLast();
        assertEquals(expect.rank, ht.getName().getRank());
        assertEquals(expect.name, ht.getName().canonicalNameComplete());
      }
      
      assertEquals(TaxonomicStatus.ACCEPTED, t.getStatus());
      assertEquals("Tester", t.getAccordingTo());
      assertEquals("2008-01-01", t.getAccordingToDate().toString());
      assertFalse(t.isFossil());
      assertTrue(t.isRecent());
      assertTrue(t.getLifezones().isEmpty());
      assertNull(t.getRemarks());
      assertNull(t.getWebpage());
      
      // test synonym
      Name sn = ndao.get(dataset.getKey(), "Rho-140");
      assertEquals("Rhodacarus guevarai Guevara-Benitez, 1974", sn.canonicalNameComplete());
      
      // in acef name & taxon ids are the same
      Synonym syn = synMapper.get(dataset.getKey(), sn.getId());
      assertNotNull(syn);
      
      t = tdao.get(dataset.getKey(), "Rho-61");
      assertEquals("Multidentorhodacarus denticulatus (Berlese, 1920)", t.getName().canonicalNameComplete());
      t.setChildCount(null);
      assertEquals(t, syn.getAccepted());
    }
  }
  
  @Test
  public void testAcef6Misapplied() throws Exception {
    normalizeAndImport(ACEF, 6);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
  
      Taxon t = tdao.get(dataset.getKey(), "MD2");
      assertEquals("Latrodectus mactans", t.getName().getScientificName());
      assertEquals("(Fabricius, 1775)", t.getName().authorshipComplete());
  
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
      
      assertEquals(3, info.getTaxonReferences().size());
      for (String refId : info.getTaxonReferences()) {
        Reference r = info.getReference(refId);
        assertNotNull(r);
      }
      
      Synonymy syn = tdao.getSynonymy(t);
      assertEquals(5, syn.size());
      assertEquals(2, syn.getMisapplied().size());
      assertEquals(3, syn.getHeterotypic().size());
      assertEquals(0, syn.getHomotypic().size());
      
      Synonym s = sm.get(dataset.getKey(), "s5");
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
      Taxon annua = tdao.get(dataset.getKey(), "4");
      assertEquals("Poa annua L.", annua.getName().canonicalNameComplete());
      
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
      
      Name reptans1 = ndao.get(dataset.getKey(), "7");
      Name reptans2 = ndao.get(dataset.getKey(), "8");
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
      Name n = ndao.get(dataset.getKey(), "30405");
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
      Name n = ndao.get(dataset.getKey(), "9946");
  
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
      Name n = ndao.get(dataset.getKey(), "1000");
      assertEquals("Platycarpha glomerata", n.getScientificName());
      assertEquals("(Thunberg) A.P.de Candolle", n.authorshipComplete());
      assertEquals("1000", n.getId());
      assertEquals(Rank.SPECIES, n.getRank());
    
      // one root
      assertEquals(1, tdao.listRoot(dataset.getKey(), new Page()).getResult().size());
  
      SynonymMapper sm = session.getMapper(SynonymMapper.class);
      List<Synonym> syns = sm.listByNameID(dataset.getKey(), "1006-s3");
      assertEquals(1, syns.size());
      Synonym s3 = syns.get(0);
      assertEquals("Leonida taraxacoida Vill.", s3.getName().canonicalNameComplete());
      assertEquals("1006", s3.getAccepted().getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", s3.getAccepted().getName().canonicalNameComplete());
      
      // https://github.com/Sp2000/colplus-backend/issues/237
      VerbatimRecordMapper vm = session.getMapper(VerbatimRecordMapper.class);
      for (VerbatimRecord v : vm.list(dataset.getKey(), null, null, LogicalOperator.AND, null, new Page(0, 100))) {
        for (Term t : v.terms()) {
          assertFalse(t instanceof UnknownTerm);
        }
      }
    }
  
    DatasetImport di = metrics();
    assertEquals(3, (int) metrics().getDescriptionCount());
    assertEquals(9, (int) metrics().getDistributionCount());
    assertEquals(1, (int) metrics().getMediaCount());
    assertEquals(4, (int) metrics().getReferenceCount());
    assertEquals(1, (int) metrics().getVernacularCount());
    assertEquals(19, (int) metrics().getTaxonCount());
    assertEquals(24, (int) metrics().getNameCount());
    assertEquals(67, (int) metrics().getVerbatimCount());
    
    //assertFalse(metrics().getIssuesCount().containsKey(Issue.PARENT_ID_INVALID));
    assertEquals(5, (int) metrics().getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));
    assertEquals(metrics().getTaxonCount(), metrics().getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals(1, (int) metrics().getMediaByTypeCount().get(MediaType.IMAGE));
    assertEquals(2, (int) metrics().getNamesByRankCount().get(Rank.FAMILY));
    assertEquals(4, (int) metrics().getNamesByRankCount().get(Rank.GENUS));
    assertEquals(10, (int) metrics().getNamesByRankCount().get(Rank.SPECIES));
    assertEquals(3, (int) metrics().getNamesByRankCount().get(Rank.SUBSPECIES));
    assertEquals(9, (int) metrics().getDistributionsByGazetteerCount().get(Gazetteer.ISO));
  
  }
  
  @Test
  @Ignore("manual test for debugging entire imports")
  public void testExternalManually() throws Exception {
    // comment out if name matching is needed
    dataset.setContributesTo(null);
    
    //normalizeAndImport(URI.create("https://github.com/mdoering/data-ina/archive/master.zip"), COLDP);
    normalizeAndImport(URI.create("http://data.canadensys.net/ipt/archive.do?r=vascan"), DataFormat.DWCA);
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
    Taxon t = tdao.get(dataset.getKey(), taxonID);
    while (t.getParentId() != null) {
      Taxon parent = tdao.get(dataset.getKey(), t.getParentId());
      assertEquals(expected.pop(), parent.getId());
      t = parent;
    }
    assertTrue(expected.isEmpty());
  }
  
}
