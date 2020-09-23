package life.catalogue.db;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class TestDataRuleTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Parameterized.Parameters(name= "{index}: {0}")
  public static Iterable<Object[]> data() {
    return TestDataRule.allTestData().stream().map(t -> new Object[]{t}).collect(Collectors.toList());
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

    System.out.println("rule has run fine");
  }

  @Test
  public void insertDataAgain() {
    System.out.println("rule has run fine before the 2nd test");
  }
}