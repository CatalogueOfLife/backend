package life.catalogue.junit;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TestDataRuleTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Parameterized.Parameters(name= "{index}: {0}")
  public static Iterable<Object[]> data() {
    List<TestDataRule.TestData> list = List.of(
      TestDataRule.DRAFT,
      TestDataRule.DRAFT_NAME_UPD,
      TestDataRule.EMPTY,
      TestDataRule.KEEP,
      TestDataRule.DATASET_MIX,
      TestDataRule.APPLE,
      TestDataRule.FISH,
      TestDataRule.TREE,
      TestDataRule.TREE2,
      TestDataRule.DRAFT_WITH_SECTORS,
      TestDataRule.DUPLICATES,
      TestDataRule.NIDX,
      TestDataRule.COL_SYNCED
    );
    return list.stream().map(t -> new Object[]{t}).collect(Collectors.toList());
  }

  @Rule
  public TestDataRule dataRule;
  
  TestDataRule.TestData data;
  
  public TestDataRuleTest(TestDataRule.TestData testData) {
    System.out.println("Testing " + testData);
    data = testData;
    dataRule = new TestDataRule(data);
  }
  
  @Test
  public void insertData() {
    DatasetMapper dm = dataRule.getSqlSession().getMapper(DatasetMapper.class);
    NameMapper nm = dataRule.getSqlSession().getMapper(NameMapper.class);

    for (int key : data.datasetKeys) {
      assertNotNull(dm.get(key));
      var names = nm.list(key, new Page());
      assertParsedAuthorships(names);
    }
    if (data.key != null) {
      List<Name> list = nm.list(data.key, new Page());
      assertTrue(list.size() > 0);
      // make sure we got parsed authors if we have authors
      assertParsedAuthorships(list);
    }
    assertUsageCount();

    System.out.println("rule has run fine");
  }

  private void assertParsedAuthorships(List<Name> names) {
    for (var n : names ) {
      if (n.getAuthorship() != null) {
        assertTrue(n.toStringComplete(), n.hasParsedAuthorship());
      }
    }
  }
  @Test
  public void insertDataAgain() {
    System.out.println("rule has run fine before the 2nd test");
    assertUsageCount();
  }

  void assertUsageCount() {
    int count = data.key == null ? 0 : dataRule.getSqlSession().getMapper(NameUsageMapper.class).count(data.key);

    System.out.println("\n\n"+data.name);
    System.out.println(count);

    if(data.equals(TestDataRule.KEEP)) {
      assertEquals(0, count);
    } else if(data.equals(TestDataRule.APPLE)) {
      assertEquals(4, count);
    } else if(data.equals(TestDataRule.FISH)) {
      assertEquals(12, count);
    } else if(data.equals(TestDataRule.TREE)) {
      assertEquals(24, count);
    } else if(data.equals(TestDataRule.DRAFT)) {
      assertEquals(21, count);
    } else if(data.equals(TestDataRule.DRAFT_WITH_SECTORS)) {
      assertEquals(23, count);
    } else if(data.equals(TestDataRule.NIDX)) {
      assertEquals(0, count);
    }
  }
}