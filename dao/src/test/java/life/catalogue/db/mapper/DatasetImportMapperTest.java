package life.catalogue.db.mapper;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.ImportMetrics;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.type2.StringCount;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static org.junit.Assert.*;


/**
 *
 */
public class DatasetImportMapperTest extends MapperTestBase<DatasetImportMapper> {
  private static Random rnd = new Random();
  
  public DatasetImportMapperTest() {
    super(DatasetImportMapper.class);
  }

  static void fill(ImportMetrics m, ImportState state) {
    m.setDatasetKey(DATASET11.getKey());
    m.setCreatedBy(Users.TESTER);
    m.setJob(DatasetImportMapperTest.class.getSimpleName());
    m.setError("no error");
    m.setState(state);
    m.setStarted(LocalDateTime.now());
    m.setFinished(LocalDateTime.now());
    m.setAppliedDecisionCount(83);
    m.setBareNameCount(13);
    m.setDistributionCount(12345);
    m.setMediaCount(936);
    m.setNameCount(65432);
    m.setReferenceCount(9781);
    m.setSynonymCount(137);
    m.setTaxonCount(5748329);
    m.setTreatmentCount(432);
    m.setTypeMaterialCount(432);
    m.setVernacularCount(432);
    m.setDistributionsByGazetteerCount(mockCount(Gazetteer.class));
    m.setExtinctTaxaByRankCount(mockCount(Rank.class));
    m.setIgnoredByReasonCount(mockCount(IgnoreReason.class));
    m.setIssuesCount(mockCount(Issue.class));
    m.setMediaByTypeCount(mockCount(MediaType.class));
    m.setNameRelationsByTypeCount(mockCount(NomRelType.class));
    m.setNamesByCodeCount(mockCount(NomCode.class));
    m.setNamesByRankCount(mockCount(Rank.class));
    m.setNamesByStatusCount(mockCount(NomStatus.class));
    m.setNamesByTypeCount(mockCount(NameType.class));
    m.setNamesByMatchTypeCount(mockCount(MatchType.class));
    m.setSpeciesInteractionsByTypeCount(mockCount(SpeciesInteractionType.class));
    m.setSynonymsByRankCount(mockCount(Rank.class));
    m.setTaxaByRankCount(mockCount(Rank.class));
    m.setTaxonConceptRelationsByTypeCount(mockCount(TaxonConceptRelType.class));
    m.setTypeMaterialByStatusCount(mockCount(TypeStatus.class));
    m.setUsagesByOriginCount(mockCount(Origin.class));
    m.setUsagesByStatusCount(mockCount(TaxonomicStatus.class));
    m.setVernacularsByLanguageCount(mockCount());
  }

  private static DatasetImport create(ImportState state) throws Exception {
    DatasetImport d = new DatasetImport();
    fill(d, state);
    d.setFormat(DataFormat.COLDP);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setDownloadUri(URI.create("http://rs.gbif.org/datasets/nub.zip"));
    d.setDownload(LocalDateTime.now());
    d.setVerbatimCount(5748923);
    d.setMaxClassificationDepth(31);
    Map<Term, Integer> vcnt = new HashMap<>();
    vcnt.put(AcefTerm.AcceptedSpecies, 10);
    d.setVerbatimByTermCount(vcnt);
    Map<Term, Integer> terms = new HashMap<>();
    terms.put(AcefTerm.SpeciesEpithet, 10);
    terms.put(AcefTerm.Genus, 10);
    terms.put(AcefTerm.AcceptedTaxonID, 10);
    terms.put(AcefTerm.SubGenusName, 7);
    for (Term rt : d.getVerbatimByTermCount().keySet()) {
      d.getVerbatimByRowTypeCount().put(rt, terms);
    }
    return d;
  }
  
  private static DatasetImport create() throws Exception {
    return create(ImportState.DOWNLOADING);
  }
  
  private static Map<String, Integer> mockCount() {
    Map<String, Integer> cnt = Maps.newHashMap();
    for (String x = "abc"; !x.equalsIgnoreCase("aca"); x=StringUtils.increase(x)) {
      cnt.put(x, rnd.nextInt(Integer.MAX_VALUE));
    }
    return cnt;
  }
  
  private static <T extends Enum> Map<T, Integer> mockCount(Class<T> clazz) {
    Map<T, Integer> cnt = Maps.newHashMap();
    for (T val : clazz.getEnumConstants()) {
      cnt.put(val, rnd.nextInt(Integer.MAX_VALUE));
    }
    return cnt;
  }
  
  @Test
  public void roundtrip() throws Exception {
    DatasetImport d1 = create();
    mapper().create(d1);
    commit();
    assertEquals(1, d1.getAttempt());
    
    DatasetImport d2 = mapper().get(d1.getDatasetKey(), d1.getAttempt());

    if (!d1.equals(d2)) {
      d1.setVerbatimByRowTypeCount(null);
      d2.setVerbatimByRowTypeCount(null);
    }
    //printDiff(d1, d2);
    assertEquals(d1, d2);
    
    d1.setState(ImportState.FINISHED);
    d1.setError("no error at all");
    mapper().update(d1);
    assertNotEquals(d1, d2);

    d2 = mapper().get(d1.getDatasetKey(), d1.getAttempt());
    assertEquals(d1, d2);
    commit();
  }
  
  @Test
  public void lastSuccessful() throws Exception {
    JobSearchRequest req = new JobSearchRequest();
    req.setStates(ImmutableSet.of(ImportState.FINISHED));
    Page page = new Page();
    
    DatasetImport d = create();
    mapper().create(d);
    req.setDatasetKey(d.getDatasetKey());
    assertTrue(mapper().list(req, page).isEmpty());
    
    d.setState(ImportState.FAILED);
    d.setError("damn error");
    mapper().update(d);
    assertTrue(mapper().list(req, page).isEmpty());
    
    d = create();
    d.setState(ImportState.DOWNLOADING);
    mapper().create(d);
    req.setDatasetKey(d.getDatasetKey());
    assertTrue(mapper().list(req, page).isEmpty());
    
    d.setState(ImportState.FINISHED);
    mapper().update(d);
    assertFalse(mapper().list(req, page).isEmpty());
    
    d = create();
    d.setState(ImportState.CANCELED);
    mapper().create(d);
    req.setDatasetKey(d.getDatasetKey());
    assertFalse(mapper().list(req, page).isEmpty());
    commit();
  }


  @Test
  public void current() throws Exception {
    DatasetImport d1 = create();
    mapper().create(d1);
    final int datasetKey = d1.getDatasetKey();

    // nothing on dataset yet
    assertNull(mapper().current(datasetKey));

    DatasetImport d2 = create();
    d2.setState(ImportState.FAILED);
    d2.setError("damn error");
    mapper().create(d2);
    assertNull(mapper().current(datasetKey));

    mapper(DatasetMapper.class).updateLastImport(datasetKey, d1.getAttempt());
    var curr = mapper().current(datasetKey);
    assertNotNull(curr);
    assertEquals(d1.getAttempt(), curr.getAttempt());
  }

  @Test
  public void listCount() throws Exception {
    mapper().create(create(ImportState.FAILED));
    mapper().create(create(ImportState.FINISHED));
    mapper().create(create(ImportState.PROCESSING));
    mapper().create(create(ImportState.FINISHED));
    mapper().create(create(ImportState.CANCELED));
    mapper().create(create(ImportState.INSERTING));
    mapper().create(create(ImportState.FINISHED));

    JobSearchRequest req = new JobSearchRequest();
    assertEquals(7, mapper().count(null));
    assertEquals(7, mapper().count(req));
    req.setStates(Set.of(ImportState.FAILED));
    assertEquals(1, mapper().count(req));
    req.setStates(Set.of(ImportState.FINISHED));
    assertEquals(3, mapper().count(req));
    req.setStates(Set.of(ImportState.PROCESSING, ImportState.INSERTING));
    assertEquals(2, mapper().count(req));
    req.setDatasetKey(null);
    req.setStates(Set.of(ImportState.PROCESSING, ImportState.INSERTING));
    assertEquals(2, mapper().list(req, new Page()).size());
  }
  
  @Test
  public void counts() throws Exception {
    assertEquals((Integer) 1, mapper().countBareName(DATASET11.getKey()));
    assertEquals((Integer) 5, mapper().countDistribution(DATASET11.getKey()));
    assertEquals((Integer) 0, mapper().countMedia(DATASET11.getKey()));
    assertEquals((Integer) 5, mapper().countName(DATASET11.getKey()));
    assertEquals((Integer) 3, mapper().countReference(DATASET11.getKey()));
    assertEquals((Integer) 2, mapper().countSynonym(DATASET11.getKey()));
    assertEquals((Integer) 2, mapper().countTaxon(DATASET11.getKey()));
    assertEquals((Integer) 0, mapper().countTreatment(DATASET11.getKey()));
    assertEquals((Integer) 0, mapper().countTypeMaterial(DATASET11.getKey()));
    assertEquals((Integer) 3, mapper().countVernacular(DATASET11.getKey()));
    assertEquals((Integer) 5, mapper().countVerbatim(DATASET11.getKey()));
  }

  @Test
  public void countByMaps() throws Exception {
    assertEquals(3, mapper().countDistributionsByGazetteer(DATASET11.getKey()).size());
    assertEquals(1, mapper().countExtinctTaxaByRank(DATASET11.getKey()).size());
    assertEquals(6, mapper().countIssues(DATASET11.getKey()).size());
    assertEquals(0, mapper().countMediaByType(DATASET11.getKey()).size());
    assertEquals(1, mapper().countNameRelationsByType(DATASET11.getKey()).size());
    assertEquals(1, mapper().countNamesByCode(DATASET11.getKey()).size());
    assertEquals(1, mapper().countNamesByRank(DATASET11.getKey()).size());
    assertEquals(1, mapper().countNamesByStatus(DATASET11.getKey()).size());
    assertEquals(1, mapper().countNamesByType(DATASET11.getKey()).size());
    assertEquals(0, mapper().countSpeciesInteractionsByType(DATASET11.getKey()).size());
    assertEquals(1, mapper().countSynonymsByRank(DATASET11.getKey()).size());
    assertEquals(1, mapper().countTaxaByRank(DATASET11.getKey()).size());
    assertEquals(0, mapper().countTaxonConceptRelationsByType(DATASET11.getKey()).size());
    assertEquals(0, mapper().countTypeMaterialByStatus(DATASET11.getKey()).size());
    assertEquals(1, mapper().countUsagesByOrigin(DATASET11.getKey()).size());
    assertEquals(2, mapper().countUsagesByStatus(DATASET11.getKey()).size());
    assertEquals(3, mapper().countVernacularsByLanguage(DATASET11.getKey()).size());
  }

  @Test
  public void countVerbatimTerms() throws Exception {
    assertEquals(0, mapper().countVerbatimTerms(DATASET11.getKey(), ColdpTerm.scientificName).size());
    assertEquals(0, mapper().countVerbatimTerms(DATASET11.getKey(), UnknownTerm.build("scientificName", false)).size());
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

  @Test
  public void countMaps() throws Exception {
    Set<StringCount> expected = new HashSet<>();
    expected.add(new StringCount(Issue.ESCAPED_CHARACTERS, 1));
    expected.add(new StringCount(Issue.REFERENCE_ID_INVALID, 2));
    expected.add(new StringCount(Issue.ID_NOT_UNIQUE, 1));
    expected.add(new StringCount(Issue.URL_INVALID, 1));
    expected.add(new StringCount(Issue.INCONSISTENT_AUTHORSHIP, 1));
    expected.add(new StringCount(Issue.UNUSUAL_NAME_CHARACTERS, 1));
    assertCounts(expected, mapper().countIssues(DATASET11.getKey()));
  
    expected.clear();
    expected.add(new StringCount(Rank.SPECIES.name(), 5));
    assertCounts(expected, mapper().countNamesByRank(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(Rank.SPECIES.name(), 2));
    assertCounts(expected, mapper().countTaxaByRank(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(Origin.SOURCE, 4));
    assertCounts(expected, mapper().countUsagesByOrigin(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(NameType.SCIENTIFIC, 5));
    assertCounts(expected, mapper().countNamesByType(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(Gazetteer.TEXT, 3));
    expected.add(new StringCount(Gazetteer.TDWG, 1));
    expected.add(new StringCount(Gazetteer.ISO, 1));
    assertCounts(expected, mapper().countDistributionsByGazetteer(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount("nld", 1));
    expected.add(new StringCount("eng", 1));
    expected.add(new StringCount("deu", 1));
    assertCounts(expected, mapper().countVernacularsByLanguage(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(TaxonomicStatus.ACCEPTED, 2));
    expected.add(new StringCount(TaxonomicStatus.SYNONYM, 2));
    assertCounts(expected, mapper().countUsagesByStatus(DATASET11.getKey()));
    
    assertEmpty(mapper().countNamesByStatus(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(NomRelType.SPELLING_CORRECTION, 1));
    assertCounts(expected, mapper().countNameRelationsByType(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(AcefTerm.AcceptedSpecies.prefixedName(), 3));
    expected.add(new StringCount(AcefTerm.Synonyms.prefixedName(), 2));
    assertCounts(expected, mapper().countVerbatimByType(DATASET11.getKey()));
  }
  
  private static <T> void assertCounts(Set<T> expected, List<T> actual) {
    assertEquals(expected, new HashSet<>(actual));
  }
  
  private static <T> void assertEmpty(List<StringCount> actual) {
    if (actual.isEmpty()) return;
    for (StringCount cnt : actual) {
      assertNull(cnt.getKey());
    }
  }
  
}