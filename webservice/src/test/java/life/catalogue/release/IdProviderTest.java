package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.id.IdConverter;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import java.io.IOException;
import java.util.*;

import static life.catalogue.api.vocab.TaxonomicStatus.*;
import static org.gbif.nameparser.api.Rank.GENUS;
import static org.gbif.nameparser.api.Rank.SPECIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IdProviderTest {


  @ClassRule
  public final static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.draft();
  final int projectKey = dataRule.testData.key;

  ReleaseConfig cfg;
  Map<Integer, List<SimpleNameWithNidx>> prevIdsByAttempt = new HashMap<>();
  List<SimpleNameWithNidx> testNames = new ArrayList<>();


  @Before
  public void init() throws IOException {
    cfg = new ReleaseConfig();
    System.out.println("Create id mapping tables for project " + projectKey);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.createIdMapTable(t, projectKey));
    }
  }

  @After
  public void destroy() {
    System.out.println("Remove id mapping tables for project " + projectKey);
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
      DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dmp.deleteTable(t, projectKey));
    }
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.reportDir);
  }

  class IdTestProvider extends IdProvider {

    public IdTestProvider() {
      super(projectKey, prevIdsByAttempt.isEmpty() ? 1 : Collections.max(prevIdsByAttempt.keySet())+1, cfg, PgSetupRule.getSqlSessionFactory());
    }

    @Override
    protected void report() {
      // dont report
    }

    @Override
    protected void mapIds() {
      // map ids from test, not from DB
      mapIds(testNames);
    }

    @Override
    protected void loadPreviousReleaseIds() {
      // dont do anything. load release ids manually
      for (Map.Entry<Integer, List<SimpleNameWithNidx>> rel : prevIdsByAttempt.entrySet()) {
        int attempt = rel.getKey();
        int datasetKey = 1000 + attempt;
        for (SimpleNameWithNidx sn : rel.getValue()) {
          addReleaseId(datasetKey,attempt, sn);
        }
      }
    }

  }

  @Test
  public void nothing() throws Exception {
    IdProvider provider = new IdTestProvider();
    provider.mapIds(new ArrayList<>(List.of()));

    IdProvider.IdReport report = provider.getReport();
    assertTrue(report.created.isEmpty());
    assertTrue(report.deleted.isEmpty());
    assertTrue(report.resurrected.isEmpty());
  }

  @Test
  public void basic() throws Exception {
    // 1st attempt
    prevIdsByAttempt.put(1, List.of(
      sn(2, 2, GENUS, "Abies", null, ACCEPTED),
      sn(10, 10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(11, 11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(12, 12, SPECIES, "Picea alba ", null, SYNONYM, "Abies alba")
    ));
    // 2nd attempt
    prevIdsByAttempt.put(2, List.of(
      sn(20, 11, SPECIES, "Abies alba", "Mill", ACCEPTED),
      sn(13, 13, SPECIES, "Picea alba ", "DC.", SYNONYM, "Abies alba")
    ));

    // test names
    testNames = new ArrayList<>(List.of(
      sn(10, SPECIES, "Abies alba", null, PROVISIONALLY_ACCEPTED),
      sn(11, SPECIES, "Abies alba", "Mill.", ACCEPTED),
      sn(11, SPECIES, "Abies alba", "Mill", SYNONYM, "Abies albi")
    ));

    IdTestProvider provider = new IdTestProvider();
    IdProvider.IdReport report = provider.run();
    assertEquals(1, report.created.size());
    assertEquals(2, report.deleted.size());
    assertEquals(2, report.resurrected.size());

    assertID(10, testNames.get(0)); // resurrected
    assertID(11, testNames.get(1)); // resurrected
    assertID(21, testNames.get(2)); // different synonym parent
  }

  void assertID(int id, SimpleNameWithNidx n){
    assertEquals((Integer)id, n.getCanonicalId());
  }
  static SimpleNameWithNidx sn(int id, int nidx, Rank rank, String name, String authorship, TaxonomicStatus status){
    return sn(id, nidx, rank, name, authorship, status, null);
  }
  static SimpleNameWithNidx sn(int id, int nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    return sn(IdConverter.LATIN29.encode(id), nidx, rank, name, authorship, status, parent);
  }
  static SimpleNameWithNidx sn(int nidx, Rank rank, String name, String authorship, TaxonomicStatus status){
    return sn(UUID.randomUUID().toString(), nidx, rank, name, authorship, status, null);
  }
  static SimpleNameWithNidx sn(int nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    return sn(UUID.randomUUID().toString(), nidx, rank, name, authorship, status, parent);
  }
  static SimpleNameWithNidx sn(String id, int nidx, Rank rank, String name, String authorship, TaxonomicStatus status, String parent){
    SimpleNameWithNidx sn = new SimpleNameWithNidx();
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