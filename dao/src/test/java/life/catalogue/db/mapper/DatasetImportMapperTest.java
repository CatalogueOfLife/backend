package life.catalogue.db.mapper;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.*;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.type2.StringCount;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

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
  
  private static DatasetImport create(ImportState state) throws Exception {
    DatasetImport d = new DatasetImport();
    d.setDatasetKey(DATASET11.getKey());
    d.setCreatedBy(Users.TESTER);
    d.setError("no error");
    d.setState(state);
    d.setFormat(DataFormat.COLDP);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setStarted(LocalDateTime.now());
    d.setFinished(LocalDateTime.now());
    d.setDownloadUri(URI.create("http://rs.gbif.org/datasets/nub.zip"));
    d.setDownload(LocalDateTime.now());
    d.setVerbatimCount(5748923);
    d.setNameCount(65432);
    d.setTaxonCount(5748329);
    d.setReferenceCount(9781);
    d.setDistributionCount(12345);
    d.setVernacularCount(432);
    d.setIssuesCount(mockCount(Issue.class));
    d.setNamesByOriginCount(mockCount(Origin.class));
    d.setNamesByRankCount(mockCount(Rank.class));
    d.setTaxaByRankCount(mockCount(Rank.class));
    d.setNamesByTypeCount(mockCount(NameType.class));
    d.setVernacularsByLanguageCount(mockCount());
    d.setDistributionsByGazetteerCount(mockCount(Gazetteer.class));
    d.setUsagesByStatusCount(mockCount(TaxonomicStatus.class));
    d.setNamesByStatusCount(mockCount(NomStatus.class));
    d.setNameRelationsByTypeCount(mockCount(NomRelType.class));
    Map<Term, Integer> vcnt = new HashMap<>();
    vcnt.put(AcefTerm.AcceptedSpecies, 10);
    d.setVerbatimByTypeCount(vcnt);
    Map<Term, Integer> terms = new HashMap<>();
    terms.put(AcefTerm.SpeciesEpithet, 10);
    terms.put(AcefTerm.Genus, 10);
    terms.put(AcefTerm.AcceptedTaxonID, 10);
    terms.put(AcefTerm.SubGenusName, 7);
    for (Term rt : d.getVerbatimByTypeCount().keySet()) {
      d.getVerbatimByTermCount().put(rt, terms);
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
    
    DatasetImport d2 = mapper().list(d1.getDatasetKey(), null, new Page(0, 100)).get(0);
    d1.setAttempt(d2.getAttempt());
    
    if (!d1.equals(d2)) {
      d1.setVerbatimByTermCount(null);
      d2.setVerbatimByTermCount(null);
    }
    assertEquals(d1, d2);
    
    d1.setState(ImportState.FINISHED);
    d1.setError("no error at all");
    mapper().update(d1);
    assertNotEquals(d1, d2);
    
    d2 = mapper().list(d1.getDatasetKey(), null, new Page(0, 100)).get(0);
    assertEquals(d1, d2);
    commit();
  }
  
  @Test
  public void lastSuccessful() throws Exception {
    Set<ImportState> FINITO = ImmutableSet.of(ImportState.FINISHED);
    Page page = new Page();
    
    DatasetImport d = create();
    mapper().create(d);
    assertTrue(mapper().list(d.getDatasetKey(), FINITO, page).isEmpty());
    
    d.setState(ImportState.FAILED);
    d.setError("damn error");
    mapper().update(d);
    assertTrue(mapper().list(d.getDatasetKey(), FINITO, page).isEmpty());
    
    d = create();
    d.setState(ImportState.DOWNLOADING);
    mapper().create(d);
    assertTrue(mapper().list(d.getDatasetKey(), FINITO, page).isEmpty());
    
    d.setState(ImportState.FINISHED);
    mapper().update(d);
    assertFalse(mapper().list(d.getDatasetKey(), FINITO, page).isEmpty());
    
    d = create();
    d.setState(ImportState.CANCELED);
    mapper().create(d);
    assertFalse(mapper().list(d.getDatasetKey(), FINITO, page).isEmpty());
    commit();
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
    
    assertEquals(7, mapper().count(null, null));
    assertEquals(7, mapper().count(null, Lists.newArrayList()));
    assertEquals(1, mapper().count(null, Lists.newArrayList(ImportState.FAILED)));
    assertEquals(3, mapper().count(null, Lists.newArrayList(ImportState.FINISHED)));
    assertEquals(2, mapper().count(null, Lists.newArrayList(ImportState.PROCESSING, ImportState.INSERTING)));
    
    assertEquals(2, mapper().list(null, Lists.newArrayList(ImportState.PROCESSING, ImportState.INSERTING), new Page()).size());
  }
  
  @Test
  public void counts() throws Exception {
    assertEquals((Integer) 5, mapper().countName(DATASET11.getKey()));
    assertEquals((Integer) 2, mapper().countTaxon(DATASET11.getKey()));
    assertEquals((Integer) 3, mapper().countReference(DATASET11.getKey()));
    assertEquals((Integer) 5, mapper().countVerbatim(DATASET11.getKey()));
    assertEquals((Integer) 3, mapper().countVernacular(DATASET11.getKey()));
    assertEquals((Integer) 3, mapper().countDistribution(DATASET11.getKey()));
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.DRAFT_COL);
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
    expected.add(new StringCount(Origin.SOURCE, 5));
    assertCounts(expected, mapper().countNamesByOrigin(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(NameType.SCIENTIFIC, 5));
    assertCounts(expected, mapper().countNamesByType(DATASET11.getKey()));
    
    expected.clear();
    expected.add(new StringCount(Gazetteer.TEXT, 3));
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