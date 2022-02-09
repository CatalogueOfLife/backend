package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.db.TestDataRule;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamesIndexMapperTest extends CRUDTestBase<Integer, IndexName, NamesIndexMapper> {

  public final static TestDataRule.TestData NIDX = new TestDataRule.TestData("nidx", null, null, null);

  public NamesIndexMapperTest() {
    super(NamesIndexMapper.class, NIDX);
  }

  @Test
  public void processAll() {
    final AtomicInteger counter = new AtomicInteger();
    mapper().processAll().forEach(n -> {
      counter.incrementAndGet();
      assertNotNull(n.getKey());
      assertNotNull(n.getCanonicalId());
      assertNotNull(n.getScientificName());
      assertNotNull(n.getRank());
    });
    assertEquals(4, counter.get());
  }

  @Test
  public void regex() {
    var res = mapper().listByRegex(".", null, null);
    assertEquals(4, res.size());

    res = mapper().listByRegex("Abi", null, null);
    assertEquals(4, res.size());

    res = mapper().listByRegex(".*alb", null, null);
    assertEquals(2, res.size());

    res = mapper().listByRegex(".*ba[[:>:]]", null, null);
    assertEquals(2, res.size());

    res = mapper().listByRegex(".*a\\M", null, null);
    assertEquals(2, res.size());
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

    IndexName n2 = createTestEntity(1);
    n2.setCanonicalId(n1.getKey());
    mapper().create(n2);

    n = mapper().get(n2.getKey());
    assertEquals(n1.getKey(), n.getCanonicalId());
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