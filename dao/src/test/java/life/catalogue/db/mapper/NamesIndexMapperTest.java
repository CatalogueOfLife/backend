package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.SimpleName;
import life.catalogue.db.TestDataRule;

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
    assertEquals(4, counter.get());
  }

  private void assertNotNullProps(Iterable<IndexName> ns){
    for (var n : ns) {
      assertNotNullProps(n);
    }
  }

  private void assertNotNullProps(IndexName n){
    assertNotNull(n.getKey());
    assertNotNull(n.getCanonicalId());
    assertNotNull(n.getScientificName());
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
    assertEquals(2, counter.get());

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
    var res = mapper().listByRegex(".", false,null, null);
    assertEquals(4, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex("Abi", false,null, null);
    assertEquals(4, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*alb", false,null, null);
    assertEquals(2, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*ba[[:>:]]", false,null, null);
    assertEquals(2, res.size());
    assertNotNullProps(res);

    res = mapper().listByRegex(".*a\\M", false,null, null);
    assertEquals(2, res.size());
    assertNotNullProps(res);
  }

  @Test
  public void count() {
    assertEquals(4, mapper().count());
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
   * we can create index names with or without an explicit canonical id value.
   * In both cases the create must populate the column.
   */
  @Test
  public void create() {
    IndexName n1 = createTestEntity(1);
    n1.setCanonicalId(null);
    n1.setAuthorship(null);
    mapper().create(n1);

    IndexName n = mapper().get(n1.getKey());
    assertEquals(n.getKey(), n.getCanonicalId());
    assertNotNullProps(n);

    IndexName n2 = createTestEntity(1);
    n2.setCanonicalId(n1.getKey());
    mapper().create(n2);

    n = mapper().get(n2.getKey());
    assertEquals(n1.getKey(), n.getCanonicalId());
    assertNotNullProps(n);
  }

  @Override
  IndexName createTestEntity(int datasetKey) {
    IndexName n = new IndexName(TestEntityGenerator.newName());
    n.setCreatedBy(null);
    n.setModifiedBy(null);
    return n;
  }

  @Override
  IndexName removeDbCreatedProps(IndexName obj) {
    obj.setCanonicalId(null);
    return obj;
  }

  @Override
  void updateTestObj(IndexName obj) {
    obj.setAuthorship("Döring, 2022");
    obj.setCanonicalId(obj.getKey());
  }
}