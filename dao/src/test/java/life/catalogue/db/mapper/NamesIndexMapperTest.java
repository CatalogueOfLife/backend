package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.db.TestDataRule;
import org.gbif.nameparser.api.NameType;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamesIndexMapperTest extends CRUDTestBase<Integer, IndexName, NamesIndexMapper> {

  public final static TestDataRule.TestData NIDX = new TestDataRule.TestData("nidx", null, null, null, Map.of(
    "names_index", Map.of("type", NameType.SCIENTIFIC)
  ));

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
  public void count() {
    assertEquals(4, mapper().count());
  }

  @Test
  public void truncate() {
    mapper().truncate();
    assertEquals(0, mapper().count());

    mapper().resetSequence();
    IndexName n = createTestEntity(-1);
    mapper().create(n);
    assertEquals(2, (int) n.getKey());
  }

  @Override
  IndexName createTestEntity(int datasetKey) {
    IndexName n = new IndexName(TestEntityGenerator.newName());
    n.setCreatedBy(null);
    n.setModifiedBy(null);
    return n;
  }

  @Override
  void updateTestObj(IndexName obj) {
    obj.setAuthorship("Döring, 2022");
  }
}