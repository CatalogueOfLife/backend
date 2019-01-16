package org.col.resources;

import javax.ws.rs.core.GenericType;

import org.col.api.model.ResultPage;
import org.col.api.model.TreeNode;
import org.col.db.mapper.InitMybatisRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static org.col.dw.ApiUtils.editorCreds;
import static org.col.dw.ApiUtils.json;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TreeResourceTest extends ResourceTestBase {
  
  static GenericType<ResultPage<? extends TreeNode>> RESP_TYPE = new GenericType<ResultPage<? extends TreeNode>>() {};
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple(RULE.getSqlSessionFactory());
  
  public TreeResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void get() {
    ResultPage<? extends TreeNode> root = base.path("/11/tree").request().get(RESP_TYPE);
    assertEquals(2, root.size());
  }
  
  @Test
  @Ignore("TODO: under construction")
  public void create() {
    TreeNode tn = new TreeNode();
    tn.setDatasetKey(11);
    tn.setId("root-1");
  
    String id = editorCreds(base.path("/11/tree")
        .queryParam("datasetKey", 12)
    ).post(json(tn), String.class);
    
    assertNotNull(id);
  }
  
}