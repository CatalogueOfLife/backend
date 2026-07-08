package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.SimpleName;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.util.UnicodeUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamesIndexMapperTest extends CRUDEntityTestBase<Integer, IndexName, NamesIndexMapper> {

  public NamesIndexMapperTest() {
    super(NamesIndexMapper.class, TestDataRule.NIDX);
  }

  @Test
  public void processAll() {
    final AtomicInteger counter = new AtomicInteger();
    mapper().processAll().forEach(n -> {
      counter.incrementAndGet();
      assertNotNullProps(n);
    });
    // single-tier fixture: 2 canonical rows (Abies, Abies alba)
    assertEquals(2, counter.get());
  }

  private void assertNotNullProps(Iterable<IndexName> ns){
    for (var n : ns) {
      assertNotNullProps(n);
    }
  }

  private void assertNotNullProps(IndexName n){
    assertNotNull(n.getKey());
    assertNotNull(n.getScientificName());
    // single-tier: rank is always the canonical UNRANKED constant
    assertNotNull(n.getRank());
  }

  @Test
  public void processDatasets() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    final var ref = new TypeReference<Map<Integer, String>>() {};
    for (SimpleName n : mapper().processDatasets(List.copyOf(testDataRule.testData.datasetKeys), 1)) {
      counter.incrementAndGet();
      System.out.println(n);
      var map = ApiModule.MAPPER.readValue(n.getId(), ref);
      System.out.println(map);
    }
    // single-tier fixture: all usages match the one canonical "Abies alba" -> a single group
    assertEquals(1, counter.get());

    counter.set(0);
    for (SimpleName n : mapper().processDatasets(List.copyOf(testDataRule.testData.datasetKeys), 2)) {
      counter.incrementAndGet();
      System.out.println(n);
      var map = ApiModule.MAPPER.readValue(n.getId(), ref);
      System.out.println(map);
    }
    assertEquals(1, counter.get());
  }

  @Test
  public void regex() {
    // single-tier fixture: 2 canonical rows (Abies, Abies alba), only the species contains "alb"
    var res = mapper().listByRegex(".", false,null, null);
    assertEquals(2, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex("Abi", false,null, null);
    assertEquals(2, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*alb", false,null, null);
    assertEquals(1, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*ba[[:>:]]", false,null, null);
    assertEquals(1, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*a\\M", false,null, null);
    assertEquals(1, res.size());
    assertNotNullProps(res);
  }

  @Test
  public void count() {
    // single-tier fixture: 2 canonical rows
    assertEquals(2, mapper().count());
  }

  @Test
  public void normalizedPersisted() {
    IndexName n = new IndexName();
    n.setScientificName("Abies alba");
    // NOTE: the real key() of "Abies alba" is "abies alb", but that value is already taken by the
    // NIDX fixture's own canonical "Abies alba" row (id=2) - the unique index would reject a second
    // row with the same value. Use a distinct fixed literal here since this test only verifies
    // write/read round-tripping of the column, not the key() derivation itself.
    n.setNormalized("abies alba-roundtrip");
    mapper().create(n);
    commit();
    IndexName got = mapper().get(n.getKey());
    assertEquals("abies alba-roundtrip", got.getNormalized());
  }

  @Test
  public void truncate() {
    mapper().truncate();
    assertEquals(0, mapper().count());

    IndexName n = createTestEntity(-1);
    mapper().create(n);
    assertEquals(1, (int) n.getKey());
  }

  /**
   * The names index is single-tier: canonical_id is no longer a column and every created row is its
   * own canonical. Just verify that canonical entries round-trip through the trimmed column set.
   */
  @Test
  public void create() {
    IndexName n1 = createTestEntity(1);
    mapper().create(n1);

    IndexName n = mapper().get(n1.getKey());
    assertEquals(n1.getKey(), n.getKey());
    assertNotNullProps(n);

    IndexName n2 = createTestEntity(1);
    mapper().create(n2);

    n = mapper().get(n2.getKey());
    assertEquals(n2.getKey(), n.getKey());
    assertNotNullProps(n);
  }

  @Override
  IndexName createTestEntity(int datasetKey) {
    IndexName n = new IndexName(TestEntityGenerator.newName());
    n.setCreatedBy(null);
    n.setModifiedBy(null);
    // the normalized column is NOT NULL & unique - mirror NameIndexImpl.key() so fixtures round-trip
    n.setNormalized(UnicodeUtils.replaceNonAscii(
      SciNameNormalizer.normalize(UnicodeUtils.decompose(n.getScientificName())).toLowerCase(), '*'
    ));
    return n;
  }

  @Override
  void updateTestObj(IndexName obj) {
    obj.setScientificName("Abies updata");
  }
}