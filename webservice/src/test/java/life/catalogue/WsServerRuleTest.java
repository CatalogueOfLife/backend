 package life.catalogue;

 import org.junit.ClassRule;
 import org.junit.Test;

 import io.dropwizard.testing.ResourceHelpers;

public class WsServerRuleTest {
  
  @ClassRule
  public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));
  
  @Test
  public void setup() {
    System.out.println("WORKS!");
  }
}