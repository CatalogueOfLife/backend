package life.catalogue;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Page;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import life.catalogue.release.ExtendedReleaseIT;

import life.catalogue.release.IdProviderIT;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TestDataGeneratorRuleTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Parameterized.Parameters(name= "{index}: {0}")
  public static Iterable<Object[]> data() {
    List<TestDataRule.TestData> list = List.of(
      TestDataGenerator.MATCHING,
      TestDataGenerator.SYNCS,
      TestDataGenerator.XCOL,
      ExtendedReleaseIT.XRELEASE_DATA,
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
    if (data.key != null) {
      List<Name> list = nm.list(data.key, new Page());
      assertTrue(list.size() > 0);
    }
    System.out.println("rule has run fine");
  }

}