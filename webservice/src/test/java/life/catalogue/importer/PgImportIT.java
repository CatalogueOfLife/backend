package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.mapper.*;
import life.catalogue.importer.neo.model.RankedName;

import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static life.catalogue.api.TestEntityGenerator.setUserDate;
import static life.catalogue.api.vocab.DataFormat.*;
import static org.junit.Assert.*;

/**
 *
 */
public class PgImportIT extends PgImportITBase {

  @Test
  public void coldpMetadata() throws Exception {
    normalizeAndImport(COLDP, 19);
    assertEquals(1, dataset.getSource().size());
  }

  @Test
  public void testMetadataMerge() throws Exception {
    dataset.setTitle("First title");
    dataset.setDescription("First description");
    dataset.setContact(Agent.person("Mango", "Bird"));
    dataset.setLicense(License.CC0);
    dataset.setVersion("1.0");
    assertNull(dataset.getIssued());

    dataset.getSettings().enable(Setting.MERGE_METADATA);
    dataset.getSettings().put(Setting.DATA_FORMAT, COLDP);

    normalizeAndImport(COLDP, 29);

    assertEquals("First description", dataset.getDescription());
    assertEquals(Agent.person("Mango", "Bird"), dataset.getContact());
    assertEquals(License.CC0, dataset.getLicense());
    assertEquals("ColDP Example. The full dataset title", dataset.getTitle());
    assertEquals("v.48", dataset.getVersion());
    assertEquals(FuzzyDate.of("2018-06-01"), dataset.getIssued());
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
    
    List<NameRelation> rels = ndao.relations(n1006);
    assertEquals(1, rels.size());
    
    Name bas = ndao.getBasionym(key(dataset.getKey(), n1006.getId()));
    assertEquals("Leonida taraxacoida", bas.getScientificName());

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
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      // check species name
      Name n1 = ndao.get(key(dataset.getKey(), "1"));
      Name n2 = ndao.get(key(dataset.getKey(), "2"));
      
      assertIssue(n1, Issue.CHAINED_BASIONYM);
      assertIssue(n2, Issue.CHAINED_BASIONYM);
      
      Name n10 = ndao.get(key(dataset.getKey(), "10"));
      Name n11 = ndao.get(key(dataset.getKey(), "11"));
      Name n12 = ndao.get(key(dataset.getKey(), "12"));
      Name n13 = ndao.get(key(dataset.getKey(), "13"));
      
      assertIssue(n10, Issue.CHAINED_BASIONYM);
      assertIssue(n11, Issue.CHAINED_BASIONYM);
      assertIssue(n12, Issue.CHAINED_BASIONYM);
      assertNoIssue(n13, Issue.CHAINED_BASIONYM);
    }
  }

  @Test
  public void testSupplementary() throws Exception {
    normalizeAndImport(DWCA, 24);
    
    // verify results
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      // check species name
      Taxon tax = tdao.get(key(dataset.getKey(), "1000"));
      assertEquals("Crepis pulchra", tax.getName().getScientificName());
      
      TaxonInfo info = tdao.getTaxonInfo(tax);
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
      List<Distribution> expD = expectedDwca24Distributions();
      expD.stream().forEach(d -> {
        if (d.getArea() instanceof Country) {
          d.setArea(new AreaImpl(Gazetteer.ISO, d.getArea().getId(), d.getArea().getName()));
        }
      });
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
      Set<Distribution> expected = Sets.newHashSet(expD);

      Sets.SetView<Distribution> diff = Sets.difference(expected, imported);
      for (Distribution d : diff) {
        System.out.println(d);
      }
      assertEquals(expected, imported);
    }
  }
  
  @Test
  public void testAcef0() throws Exception {
    normalizeAndImport(ACEF, 0);
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper uMapper = session.getMapper(NameUsageMapper.class);
      vMapper = session.getMapper(VerbatimRecordMapper.class);
      
      Name n = ndao.get(key(dataset.getKey(), "s7"));
      assertEquals("Astragalus nonexistus", n.getScientificName());
      assertEquals("DC.", n.getAuthorship());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.ACCEPTED_ID_INVALID);
      
      // a bare name
      assertTrue(uMapper.listByNameID(dataset.getKey(), n.getId(), new Page()).isEmpty());
      assertNull(tdao.get(key(dataset.getKey(), "s7")));
      
      n = ndao.get(key(dataset.getKey(), "s6"));
      assertEquals("Astragalus beersabeensis", n.getScientificName());
      assertEquals(Rank.SPECIES, n.getRank());
      assertIssue(n, Issue.SYNONYM_DATA_MOVED);
      
      List<NameUsageBase> syns = uMapper.listByNameID(dataset.getKey(), n.getId(), new Page());
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
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Taxon t = tdao.get(key(dataset.getKey(), "14649"));
      assertEquals("Zapoteca formosa", t.getName().getScientificName());
      assertEquals("(Kunth) H.M.Hern.", t.getName().getAuthorship());
      assertEquals(Rank.SPECIES, t.getName().getRank());
      
      TaxonInfo info = tdao.getTaxonInfo(t);
      // distributions
      assertEquals(3, info.getDistributions().size());
      Set<String> areas = Sets.newHashSet("AGE-BA", "BZC-MS", "BZC-MT");
      for (Distribution d : info.getDistributions()) {
        assertEquals(Gazetteer.TDWG, d.getArea().getGazetteer());
        assertTrue(areas.remove(d.getArea().getId()));
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
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
      
      assertEquals(5, info.getReferences().size());
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
    
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Taxon annua = tdao.get(key(dataset.getKey(), "4"));
      assertEquals("Poa annua L.", annua.getName().getLabel());
      
      TaxonInfo info = tdao.getTaxonInfo(annua);
      Reference pubIn = info.getReference(annua.getName().getPublishedInId());
      assertEquals("Sp. Pl. 1: 68 (1753).", pubIn.getCitation());
      
      Synonymy syn = tdao.getSynonymy(annua);
      assertEquals(4, syn.size());
      assertEquals(0, syn.getMisapplied().size());
      assertEquals(3, syn.getHeterotypic().size());
      assertEquals(1, syn.getHomotypic().size());

      NameRelationMapper relMapper = session.getMapper(NameRelationMapper.class);
      // Poa annua has not explicitly declared a basionym
      assertTrue(relMapper.listByName(annua.getName()).isEmpty());
      
      Name reptans1 = ndao.get(key(dataset.getKey(), "7"));
      Name reptans2 = ndao.get(key(dataset.getKey(), "8"));
      assertEquals(1, relMapper.listByName(reptans1).size());
      assertEquals(1, relMapper.listByRelatedName(reptans2).size());
      
      NameRelation act = relMapper.listByName(reptans1).get(0);
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
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      Name n = ndao.get(key(dataset.getKey(), "9946"));
  
      List<NameUsageBase> usages = num.listByNameID(n.getDatasetKey(), n.getId(), new Page());
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
    testColdpSpecsMetrics(metrics());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = ndao.get(key(dataset.getKey(), "1000"));
      assertEquals("Platycarpha glomerata", n.getScientificName());
      assertEquals("(Thunberg) A. P. de Candolle", n.getAuthorship());
      assertEquals("1000", n.getId());
      assertEquals(Rank.SPECIES, n.getRank());
    
      // one root
      assertEquals(1, tdao.listRoot(dataset.getKey(), new Page()).getResult().size());
  
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      List<NameUsageBase> syns = um.listByNameID(dataset.getKey(), "1006-s3", new Page());
      assertEquals(1, syns.size());
      Synonym s3 = (Synonym) syns.get(0);
      assertEquals("Leonida taraxacoida Vill.", s3.getName().getLabel());
      assertEquals("1006", s3.getAccepted().getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", s3.getAccepted().getName().getLabel());
      
      // https://github.com/Sp2000/colplus-backend/issues/237
      VerbatimRecordMapper vm = session.getMapper(VerbatimRecordMapper.class);
      for (VerbatimRecord v : vm.list(dataset.getKey(), null, null, LogicalOperator.AND, null, null, null, new Page(0, 100))) {
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

      EstimateMapper em = session.getMapper(EstimateMapper.class);
      List<SpeciesEstimate> estimates = em.list(dataset.getKey(), new Page());
      assertEquals(2, estimates.size());
      estimates.forEach(est -> {
        assertTrue(est.getEstimate() > 1000);
        assertNotNull(est.getReferenceId());
        assertEquals(dataset.getKey(), est.getDatasetKey());
      });
    }

    // now try to import again to make sure deletions of previous data work
    store.closeAndDelete();
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
    normalizeAndImport(COLDP, 0);
    testColdpSpecsMetrics(metrics());
  }

  private void testColdpSpecsMetrics(DatasetImport di) {
    assertEquals(2, (int) metrics().getTreatmentCount());
    assertEquals(9, (int) di.getDistributionCount());
    assertEquals(1, (int) di.getMediaCount());
    assertEquals(11, (int) di.getReferenceCount());
    assertEquals(2, (int) di.getVernacularCount());
    assertEquals(23, (int) di.getTaxonCount());
    assertEquals(3, (int) di.getTypeMaterialCount());
    assertEquals(28, (int) di.getNameCount());
    assertEquals(94, (int) di.getVerbatimCount());
    assertEquals(2, (int) di.getEstimateCount());

    assertEquals(1, di.getTaxonConceptRelationsByTypeCount().size());
    assertEquals(1, di.getSpeciesInteractionsByTypeCount().size());

    //assertFalse(metrics().getIssuesCount().containsKey(Issue.PARENT_ID_INVALID));
    assertEquals(5, (int) di.getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));
    // 1 provisional status taxon
    assertEquals((int) metrics().getTaxonCount(), 1 + di.getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals(1,  (int) di.getMediaByTypeCount().get(MediaType.IMAGE));
    assertEquals(2,  (int) di.getNamesByRankCount().get(Rank.FAMILY));
    assertEquals(4,  (int) di.getNamesByRankCount().get(Rank.GENUS));
    assertEquals(12, (int) di.getNamesByRankCount().get(Rank.SPECIES));
    assertEquals(3,  (int) di.getNamesByRankCount().get(Rank.SUBSPECIES));
    assertEquals(9,  (int) di.getDistributionsByGazetteerCount().get(Gazetteer.ISO));
    assertEquals(2,  (int) di.getTypeMaterialByStatusCount().get(TypeStatus.HOLOTYPE));
  }
  
  /**
   * Rich FishBase extract
   * https://github.com/Sp2000/ConversionTool/issues/6
   */
  @Test
  public void acefSynonymRefs() throws Exception {
    normalizeAndImport(ACEF, 18);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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
  public void coldpMissingName() throws Exception {
    normalizeAndImport(COLDP, 17);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Name n = ndao.get(key(dataset.getKey(), "1"));
      assertEquals("Platycarpha glomerata", n.getScientificName());
      assertEquals("(Thunberg) A. P. de Candolle", n.getAuthorship());
      assertEquals("1", n.getId());
      assertEquals(Rank.SPECIES, n.getRank());

      // one root
      assertEquals(1, tdao.listRoot(dataset.getKey(), new Page()).getResult().size());

      // test taxa & synonyms
      assertEquals(n, tdao.get(key(dataset.getKey(), "1")).getName());
      assertNull(tdao.get(key(dataset.getKey(), "2")));
      assertNull(tdao.get(key(dataset.getKey(), "3")));
      assertNull(sdao.get(key(dataset.getKey(), "3")));
    }
  }
  
  @Test
  @Ignore("manual test for debugging entire imports")
  public void testExternalManually() throws Exception {
    dataset.setType(DatasetType.TAXONOMIC);

    //normalizeAndImportArchive(new File("/Users/markus/Downloads/dataset-253814.zip"), DWCA);
    normalizeAndImport(URI.create("https://bdj.pensoft.net/lib/ajax_srv/archive_download.php?archive_type=2&document_id=80487"), DWCA);
    //normalizeAndImport(URI.create("https://tb.plazi.org/GgServer/dwca/CB7EFFE7FFD3FFB3E551FFBDFF9C916F.zip"), DWCA);
    //normalizeAndImport(URI.create("https://github.com/mdoering/data-ina/archive/master.zip"), COLDP);
    //normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/162.tar.gz"), ACEF);
    //normalizeAndImportArchive(new File("/Users/markus/Downloads/export.zip"), COLDP);

    //normalizeAndImport(URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/higher-classification.dwca.zip"), DWCA);
    //normalizeAndImportFolder(new File("/Users/markus/code/col+/data-staphbase/coldp"), COLDP);
    //normalizeAndImport(URI.create("https://plutof.ut.ee/ipt/archive.do?r=unite_sh"), DataFormat.DWCA);
    //normalizeAndImportArchive(new File("/home/ayco/git-repos/colplus-repo/DWCA/itis_global.zip"), DWCA);
    //normalizeAndImportArchive(new File("/Users/markus/Downloads/data-ina-master.zip"), COLDP);
  }

}
