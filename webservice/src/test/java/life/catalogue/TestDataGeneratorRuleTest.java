package life.catalogue;

import life.catalogue.api.model.Page;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.release.IdProviderIT;
import life.catalogue.release.XReleaseBasicIT;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class TestDataGeneratorRuleTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Parameterized.Parameters(name= "{index}: {0}")
  public static Iterable<Object[]> data() {
    List<TestDataRule.TestData> list = List.of(
      TestDataGenerator.SYNCS,
      XReleaseBasicIT.XRELEASE_DATA,
      TestDataGenerator.MATCHING,
      TestDataGenerator.XCOL,
      TestDataGenerator.GROUPING,
      IdProviderIT.PROJECT_DATA
    );
    return list.stream().map(t -> new Object[]{t}).collect(Collectors.toList());
  }

  @Rule
  public TestDataRule dataRule;

  TestDataRule.TestData data;

  public TestDataGeneratorRuleTest(TestDataRule.TestData testData) {
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
    System.out.println("rule has run fine");
  }

}