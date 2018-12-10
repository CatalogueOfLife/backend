package org.col.resources;

import java.util.List;
import javax.ws.rs.core.GenericType;

import org.col.api.model.TreeNode;
import org.col.db.mapper.InitMybatisRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static org.col.dw.ApiUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TreeResourceTest extends ResourceTestBase {
  
  static GenericType<List<TreeNode>> LIST_TYPE = new GenericType<List<TreeNode>>() {};
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple(RULE.getSqlSessionFactory());
  
  public TreeResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void get() {
    List<TreeNode> root = base.path("/11/tree").request().get(LIST_TYPE);
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