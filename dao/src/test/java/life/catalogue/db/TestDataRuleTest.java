package life.catalogue.db;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NamesIndexMapperTest;

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
      TestDataRule.DUPLICATES,
      TestDataRule.KEEP,
      TestDataRule.APPLE,
      TestDataRule.FISH,
      TestDataRule.TREE,
      TestDataRule.DRAFT,
      TestDataRule.DRAFT_WITH_SECTORS,
      TestDataRule.DATASETS,
      NamesIndexMapperTest.NIDX
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
      nm.list(key, new Page());
    }
    if (data.key != null) {
      List<Name> list = nm.list(data.key, new Page());
      assertTrue(list.size() > 0);
    }
    assertUsageCount();

    System.out.println("rule has run fine");
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
    } else if(data.equals(TestDataRule.DATASETS)) {
      assertEquals(0, count);
    } else if(data.equals(TestDataRule.APPLE)) {
      assertEquals(4, count);
    } else if(data.equals(TestDataRule.FISH)) {
      assertEquals(12, count);
    } else if(data.equals(TestDataRule.TREE)) {
      assertEquals(24, count);
    } else if(data.equals(TestDataRule.DRAFT)) {
      assertEquals(18, count);
    } else if(data.equals(TestDataRule.DRAFT_WITH_SECTORS)) {
      assertEquals(23, count);
    } else if(data.equals(NamesIndexMapperTest.NIDX)) {
      assertEquals(0, count);
    }
  }
}