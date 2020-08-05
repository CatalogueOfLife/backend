package life.catalogue.db;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class TestDataRuleTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Parameterized.Parameters(name= "{index}: {0}")
  public static Iterable<Object[]> data() {
    return Arrays.stream(TestDataRule.TestData.values()).map(t -> new Object[]{t}).collect(Collectors.toList());
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
    System.out.println("rule has run fine");
  }

  @Test
  public void insertDataAgain() {
    System.out.println("rule has run fine before the 2nd test");
  }
}