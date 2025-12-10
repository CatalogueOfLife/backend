package life.catalogue.release;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.id.IdConverter;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.UsageMatcherMemStore;
import life.catalogue.matching.UsageMatcherStore;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.jupiter.api.Disabled;

import static life.catalogue.api.vocab.TaxonomicStatus.*;
import static org.gbif.nameparser.api.Rank.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IdProviderTest {

  @ClassRule
  public final static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.draft();
  final int projectKey = dataRule.testData.key;

  ReleaseConfig cfg;
  ProjectReleaseConfig prCfg;
  Map<Integer, List<ArchivedNameUsageMapper.ArchivedSimpleName>> prevIdsByAttempt = new HashMap<>();
  List<SimpleNameCached> testNames = new ArrayList<>();

  @Before
  public void init() throws IOException {
    cfg = new ReleaseConfig();
    prCfg = new ProjectReleaseConfig();
    System.out.println("Create id mapping tables for project " + projectKey);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, projectKey));
    }
  }

  @After
  public void destroy() {
    System.out.println("Remove id mapping tables for project " + projectKey);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.dropTable(t, projectKey));
    }
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.reportDir);
  }

  class IdTestProvider extends IdProvider {

    public IdTestProvider() {
      super(projectKey, projectKey, DatasetOrigin.RELEASE,
        prevIdsByAttempt.isEmpty() ? 1 : Collections.max(prevIdsByAttempt.keySet())+1,
        prevIdsByAttempt.isEmpty() ? -1 : Collections.max(prevIdsByAttempt.keySet())+1000,
        cfg, prCfg, SqlSessionFactoryRule.getSqlSessionFactory()
      );
    }

    @Override
    protected void report() {
      // dont report
    }

    @Override
    protected void mapAllIds() {
      // map ids from test, not from DB
      UsageMatcherStore store = new UsageMatcherMemStore(projectKey);
      for (var sn : testNames) {
        store.add(sn);
      }
      mapIds(store, -1);
    }

    private int datasetKey(int attempt) {
      return 1000 + attempt;
    }

    @Override
    protected void loadPreviousReleaseIds() {
      // dont do anything. load release ids manually
      final LoadStats stats = new LoadStats();
      // go through all names and aggregate them on their id
      Map<String, ArchivedNameUsageMapper.ArchivedSimpleName> names = new  HashMap<>();
      for (Map.Entry<Integer, List<ArchivedNameUsageMapper.ArchivedSimpleName>> rel : prevIdsByAttempt.entrySet()) {
        int attempt = rel.getKey();
        int datasetKey = datasetKey(attempt);
        getDatasetAttemptMap().put(datasetKey, attempt);
        for (var sn : rel.getValue()) {
          if (names.containsKey(sn.getId())) {
            sn.setReleaseKeys(ArrayUtils.add(sn.getReleaseKeys(), datasetKey));
          } else {
            sn.setReleaseKeys(new int[] {datasetKey});
            names.put(sn.getId(), sn);
          }
        }
      }
      // finally add
      for (var sn : names.values()) {
        addReleaseId(sn, stats);
      }
    }

    @Override
    protected Integer loadReleaseAttempts() {
      int attempt = prevIdsByAttempt.isEmpty() ? 0 : prevIdsByAttempt.keySet().stream().mapToInt(Integer::intValue).max().getAsInt();
      int lastReleaseKey = datasetKey(attempt);
      addRelease(new Release(lastReleaseKey, DatasetOrigin.RELEASE, attempt)); // we add the max attempt as the last release
      super.loadReleaseAttempts();
      return lastReleaseKey;
    }
  }

  @Test
  public void nothing() throws Exception {
    IdProvider provider = new IdTestProvider();
    provider.mapAllIds();

    IdProvider.IdReport report = provider.getReport();
    assertTrue(report.created.isEmpty());
    assertTrue(report.deleted.isEmpty());
    assertTrue(report.resurrected.isEmpty());
  }

  @Test
  public void basic() throws Exception {
    // 1st attempt
    prevIdsByAttempt.put(1, List.of(
      sn(2,0,  2, GENUS, "Abies", null, ACCEPTED),
      sn(10,1,  10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(11,1,  11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(12,2,  12, SPECIES, "Picea alba ", null, SYNONYM, "Abies alba")
    ));
    // 2nd attempt
    prevIdsByAttempt.put(2, List.of(
      sn(20,1,  11, SPECIES, "Abies alba", "Mill", ACCEPTED),
      sn(13,2, 13, SPECIES, "Picea alba ", "DC.", SYNONYM, "Abies alba")
    ));

    // test names
    testNames = new ArrayList<>(List.of(
      sn(1, 10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill", SYNONYM, "Abies albi")
    ));

    IdTestProvider provider = new IdTestProvider();
    provider.mapAllIds();
    IdProvider.IdReport report = provider.getReport();
    assertEquals(0, report.created.size());
    assertEquals(1, report.deleted.size());
    assertEquals(2, report.resurrected.size());

    assertID(10, testNames.get(0)); // resurrected
    assertID(11, testNames.get(1)); // current Mill. matches oldest Mill
    assertID(20, testNames.get(2)); // different synonym parent
  }

  @Test
  @Disabled("work in progress")
  public void misappliedAndUnparsable() throws Exception {
    // 1st attempt
    prevIdsByAttempt.put(1, List.of(
      sn(2,0, 2, GENUS, "Abies", null, ACCEPTED),
      sn(10,1,  10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(11,1,  11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(12,2,  12, SPECIES, "Picea alba ", null, SYNONYM, "Abies alba")
    ));

    // test names
    testNames = new ArrayList<>(List.of(
      sn(1, 10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill", SYNONYM, "Abies albi")
    ));

    IdTestProvider provider = new IdTestProvider();
    provider.mapAllIds();
    IdProvider.IdReport report = provider.getReport();
    assertEquals(1, report.created.size());
    assertEquals(2, report.deleted.size());
    assertEquals(2, report.resurrected.size());

    assertID(10, testNames.get(0)); // resurrected
    assertID(11, testNames.get(1)); // resurrected
    assertID(21, testNames.get(2)); // different synonym parent
  }

  @Test
  public void unmatched() throws Exception {
    // 1st attempt
    prevIdsByAttempt.put(1, List.of(
      sn(1, 10, SPECIES_AGGREGATE, "Abies alba", null, ACCEPTED),
      sn(1, 10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(100, 1, 11, SPECIES, "Abies alba", "Mill.", PROVISIONALLY_ACCEPTED)
    ));

    // test names
    testNames = new ArrayList<>(List.of(
      sn(1, 10, SPECIES, "Abies alba", null, ACCEPTED),
      sn(1, 11, SPECIES, "Abies alba", "Mill.", ACCEPTED)
    ));

    IdTestProvider provider = new IdTestProvider();
    provider.mapAllIds();
    IdProvider.IdReport report = provider.getReport();
    assertEquals(1, report.created.size());
    assertEquals(0, report.deleted.size());
    assertEquals(0, report.resurrected.size());

    assertID(101, testNames.get(0)); // new
    assertID(100, testNames.get(1)); // existing
  }

  void assertID(int id, SimpleNameWithNidx n){
    assertEquals((Integer)id, n.getCanonicalId());
  }

  static ArchivedNameUsageMapper.ArchivedSimpleName sn(int id, Integer canonId, Integer nidx, Rank rank, String name, String authorship, TaxonomicStatus status){
    return sn(id, canonId,nidx, rank, name, authorship, status, null);
  }
  static ArchivedNameUsageMapper.ArchivedSimpleName sn(int id, Integer canonId, Integer nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    return sn(IdConverter.LATIN29.encode(id), canonId, nidx, rank, name, authorship, status, parent);
  }
  static ArchivedNameUsageMapper.ArchivedSimpleName sn(Integer canonId, Integer nidx, Rank rank, String name, String authorship, TaxonomicStatus status){
    return sn(UUID.randomUUID().toString(), canonId, nidx, rank, name, authorship, status, null);
  }
  static ArchivedNameUsageMapper.ArchivedSimpleName sn(Integer canonId, Integer nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    return sn(UUID.randomUUID().toString(), canonId, nidx, rank, name, authorship, status, parent);
  }
  static ArchivedNameUsageMapper.ArchivedSimpleName sn(String id, Integer canonId, Integer nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    var sn = new ArchivedNameUsageMapper.ArchivedSimpleName();
    // nidx
    sn.setCanonicalId(canonId);
    sn.setNamesIndexId(nidx);
    sn.setNamesIndexMatchType(MatchType.EXACT);
    // simple name
    sn.setId(id);
    sn.setRank(rank);
    sn.setStatus(status);
    sn.setName(name);
    sn.setAuthorship(authorship);
    sn.setParent(parent);
    return sn;
  }
}