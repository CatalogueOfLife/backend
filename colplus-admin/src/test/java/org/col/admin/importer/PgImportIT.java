package org.col.admin.importer;

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
import org.col.admin.config.ImporterConfig;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.importer.neo.model.RankedName;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.db.PgSetupRule;
import org.col.db.dao.DatasetImportDao;
import org.col.db.dao.NameDao;
import org.col.db.dao.ReferenceDao;
import org.col.db.dao.TaxonDao;
import org.col.db.mapper.*;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import static org.col.api.vocab.DataFormat.*;
import static org.junit.Assert.*;

/**
 *
 */
public class PgImportIT {
  
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;
  private VerbatimRecordMapper vMapper;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.empty();
  
  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    dataset = new Dataset();
    dataset.setContributesTo(Catalogue.PCAT);
  }
  
  @After
  public void cleanup() throws Exception {
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
      // this creates a new datasetKey, usually 2000!
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), cfg);
      store.put(dataset);
      Normalizer norm = new Normalizer(store, source, NameIndexFactory.memory(Datasets.PROV_CAT, PgSetupRule.getSqlSessionFactory()));
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), cfg);
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), icfg);
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
  
  void normalizeAndImport(File file, DataFormat format) throws Exception {
    dataset.setDataFormat(format);
    // decompress
    ExternalSourceUtil.consumeFile(file, this::normalizeAndImport);
  }
  
  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory()).generateMetrics(dataset.getKey());
  }
  
  
  
  @Test
  public void testPublishedIn() throws Exception {
    normalizeAndImport(DWCA, 0);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameDao ndao = new NameDao(session);
      ReferenceDao rdao = new ReferenceDao(session);
      
      Name trametes_modesta = ndao.get(dataset.getKey(), "324805");
      
      Reference pubIn = rdao.get(dataset.getKey(), trametes_modesta.getPublishedInId(), trametes_modesta.getPublishedInPage());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getCitation());
      assertEquals(".neodb.94QL", pubIn.getId());
    }
  }
  
  @Test
  public void testDwca1() throws Exception {
    normalizeAndImport(DWCA, 1);
    
    // verify results
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonDao tdao = new TaxonDao(session);
      NameDao ndao = new NameDao(session);
      
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
  }
  
  @Test
  public void testIpniDwca() throws Exception {
    normalizeAndImport(DWCA, 27);
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
      NameDao dao = new NameDao(session);
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      // check species name
      Name n1 = dao.get(dataset.getKey(), "1");
      Name n2 = dao.get(dataset.getKey(), "2");
      
      assertEquals(n2.getHomotypicNameId(), n1.getHomotypicNameId());
      assertTrue(n1.getId().equals(n2.getHomotypicNameId())
          || n2.getId().equals(n2.getHomotypicNameId()));
      assertIssue(n1, Issue.CHAINED_BASIONYM);
      assertIssue(n2, Issue.CHAINED_BASIONYM);
      
      Name n10 = dao.get(dataset.getKey(), "10");
      Name n11 = dao.get(dataset.getKey(), "11");
      Name n12 = dao.get(dataset.getKey(), "12");
      Name n13 = dao.get(dataset.getKey(), "13");
      
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
  
  @Test
  public void testSupplementary() throws Exception {
    normalizeAndImport(DWCA, 24);
    
    // verify results
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonDao tdao = new TaxonDao(session);
      
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
      
      assertEquals(expD.size(), info.getDistributions().size());
      // remove dist keys before we check equality
      info.getDistributions().forEach(d -> {
        assertNotNull(d.getKey());
        assertNotNull(d.getVerbatimKey());
        d.setKey(null);
        d.setVerbatimKey(null);
      });
      Set<Distribution> imported = Sets.newHashSet(info.getDistributions());
      
      Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
      for (Distribution d : diff) {
        // System.out.println(d);
      }
      assertEquals(expD, imported);
    }
  }
  
  @Test
  public void testAcef0() throws Exception {
    normalizeAndImport(ACEF, 0);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonDao tdao = new TaxonDao(session);
      NameDao ndao = new NameDao(session);
      TaxonMapper taxMapper = session.getMapper(TaxonMapper.class);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      Name n = ndao.get(dataset.getKey(), "s7");
      assertEquals("Astragalus nonexistus DC.", n.canonicalNameComplete());
      assertEquals("Astragalus nonexistus", n.getScientificName());
      assertEquals("DC.", n.authorshipComplete());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.ACCEPTED_ID_INVALID);
      
      // a bare name
      assertTrue(taxMapper.listByName(dataset.getKey(), n.getId()).isEmpty());
      assertTrue(synMapper.listByName(dataset.getKey(), n.getId()).isEmpty());
      assertNull(tdao.get(dataset.getKey(), "s7"));
      
      n = ndao.get(dataset.getKey(), "s6");
      assertEquals("Astragalus beersabeensis", n.getScientificName());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.SYNONYM_DATA_MOVED);
      
      List<Synonym> syns = synMapper.listByName(dataset.getKey(), n.getId());
      assertEquals(1, syns.size());
      Synonym s = syns.get(0);
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
      TaxonDao tdao = new TaxonDao(session);
      
      Taxon t = tdao.get(dataset.getKey(), "14649");
      assertEquals("Zapoteca formosa (Kunth) H.M.Hern.", t.getName().canonicalNameComplete());
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
      NameDao ndao = new NameDao(session);
      TaxonDao tdao = new TaxonDao(session);
      SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
      
      Taxon t = tdao.get(dataset.getKey(), "Rho-144");
      assertEquals("Afrogamasellus lokelei Daele, 1976", t.getName().canonicalNameComplete());
      
      List<Taxon> classific = tdao.getClassification(t);
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
      assertNull(t.getDatasetUrl());
      
      // test synonym
      Name sn = ndao.get(dataset.getKey(), "Rho-140");
      assertEquals("Rhodacarus guevarai Guevara-Benitez, 1974", sn.canonicalNameComplete());
      
      List<Synonym> syns = synMapper.listByName(dataset.getKey(), sn.getId());
      assertEquals(1, syns.size());
      
      t = tdao.get(dataset.getKey(), "Rho-61");
      assertEquals("Multidentorhodacarus denticulatus (Berlese, 1920)", t.getName().canonicalNameComplete());
      t.setChildCount(null);
      assertEquals(t, syns.get(0).getAccepted());
    }
  }
  
  @Test
  public void testAcef6Misapplied() throws Exception {
    normalizeAndImport(ACEF, 6);
    
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      TaxonDao tdao = new TaxonDao(session);
      
      Taxon t = tdao.get(dataset.getKey(), "MD2");
      assertEquals("Latrodectus mactans (Fabricius, 1775)", t.getName().canonicalNameComplete());
      
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
      
      Synonym s = tdao.getSynonym(dataset.getKey(), "s5");
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
      TaxonDao tdao = new TaxonDao(session);
      
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
      
      NameDao ndao = new NameDao(session);
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
      NameDao ndao = new NameDao(session);
      Name n = ndao.get(dataset.getKey(), "30405");
      assertEquals("Haematomma ochroleucum var. porphyrium", n.getScientificName());
      assertEquals("30405", n.getId());
      
      TaxonDao tdao = new TaxonDao(session);
      // this is buggy normalization of bad data - should really be just one...
      assertEquals(2, tdao.list(dataset.getKey(), true, new Page()).getResult().size());
    }
  }
  
  @Test
  public void testColdpSpecs() throws Exception {
    normalizeAndImport(COLDP, 0);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameDao ndao = new NameDao(session);
      Name n = ndao.get(dataset.getKey(), "1000");
      assertEquals("Platycarpha glomerata", n.getScientificName());
      assertEquals("(Thunberg) A.P.de Candolle", n.authorshipComplete());
      assertEquals("1000", n.getId());
      assertEquals(Rank.SPECIES, n.getRank());
    
      TaxonDao tdao = new TaxonDao(session);
      // one root
      assertEquals(1, tdao.list(dataset.getKey(), true, new Page()).getResult().size());
  
      Synonym s3 = tdao.getSynonym(dataset.getKey(), "1006-s3");
      assertEquals("Leonida taraxacoida Vill.", s3.getName().canonicalNameComplete());
      assertEquals("1006", s3.getAccepted().getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", s3.getAccepted().getName().canonicalNameComplete());
    }
  
    DatasetImport di = metrics();
    assertEquals(0, (int) metrics().getDescriptionCount());
    assertEquals(0, (int) metrics().getDistributionCount());
    assertEquals(0, (int) metrics().getMediaCount());
    assertEquals(0, (int) metrics().getReferenceCount());
    assertEquals(0, (int) metrics().getVernacularCount());
    assertEquals(16, (int) metrics().getTaxonCount());
    assertEquals(21, (int) metrics().getNameCount());
    assertEquals(43, (int) metrics().getVerbatimCount());
    //TODO: add more comparisons when the data is richer
    // assertEquals(null, metrics().getIssuesCount());
    // assertEquals(null, metrics().getUsagesByStatusCount());
    // assertEquals(null, metrics().getVerbatimByTypeCount());
    // assertEquals(null, metrics().getMediaByTypeCount());
    // assertEquals(null, metrics().getNamesByRankCount());
  
  }
  
  @Test
  @Ignore
  public void testGsdGithub() throws Exception {
    dataset.setContributesTo(Catalogue.PCAT);
    // normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/assembly/15.tar.gz"), DataFormat.ACEF);
    // normalizeAndImport(URI.create("http://services.snsb.info/DTNtaxonlists/rest/v0.1/lists/DiversityTaxonNames_Fossils/1154/dwc"), DataFormat.DWCA);
    //normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/177.tar.gz"), DataFormat.ACEF);
    //normalizeAndImport(URI.create("https://svampe.databasen.org/dwc/DMS_Fun_taxa.zip"), DataFormat.DWCA);
    normalizeAndImport(new File("/Users/markus/Downloads/Neuropterida_ACEF_CoLPlus.zip"), DataFormat.ACEF);
  }
  
  private static RankedName rn(Rank rank, String name) {
    return new RankedName(null, name, null, rank);
  }
  
  private Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
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
