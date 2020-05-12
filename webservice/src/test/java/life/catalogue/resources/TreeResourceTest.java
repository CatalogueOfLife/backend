package life.catalogue.resources;

import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.TreeNode;
import life.catalogue.db.TestDataRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.core.GenericType;

import static life.catalogue.ApiUtils.userCreds;
import static org.junit.Assert.assertEquals;

public class TreeResourceTest extends ResourceTestBase {

  public static class TreeNodeProps extends TreeNode {
    private String name;
    private String labelHtml;
    private String authorship;

    public void setName(String name) {
      this.name = name;
    }

    public void setAuthorship(String authorship) {
      this.authorship = authorship;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getLabelHtml() {
      return labelHtml;
    }

    public void setLabelHtml(String labelHtml) {
      this.labelHtml = labelHtml;
    }

    @Override
    public String getAuthorship() {
      return authorship;
    }
  }

  static GenericType<ResultPage<TreeNodeProps>> RESP_TYPE = new GenericType<ResultPage<TreeNodeProps>>() {};
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple(RULE.getSqlSessionFactory());
  
  public TreeResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void get() {
    ResultPage<TreeNodeProps> root = userCreds(base.path("/11/tree")
        .queryParam("insertPlaceholder", true)
        .queryParam("type", TreeNode.Type.SOURCE.name())
    ).get(RESP_TYPE);
    assertEquals(2, root.size());
    // make sure we get the html markup
    assertEquals("Larus fuscus", root.getResult().get(0).getName());
    assertEquals("<i>Larus</i> <i>fuscus</i>", root.getResult().get(0).getLabelHtml());
  }
  
}