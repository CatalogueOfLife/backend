package life.catalogue.resources;

import javax.ws.rs.core.GenericType;

import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.TreeNode;
import life.catalogue.db.mapper.TestDataRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreeResourceTest extends ResourceTestBase {
  
  static GenericType<ResultPage<TreeNode>> RESP_TYPE = new GenericType<ResultPage<TreeNode>>() {};
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple(RULE.getSqlSessionFactory());
  
  public TreeResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void get() {
    ResultPage<TreeNode> root = base.path("/11/tree").request().get(RESP_TYPE);
    assertEquals(2, root.size());
    // make sure we get the html markup
    assertEquals("Larus fuscus", root.getResult().get(0).getName());
    assertEquals("<i>Larus</i> <i>fuscus</i>", root.getResult().get(0).getFormattedName());
  }
  
}