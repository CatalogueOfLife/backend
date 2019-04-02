package org.col.resources;

import javax.ws.rs.core.GenericType;

import org.col.api.model.ResultPage;
import org.col.api.model.TreeNode;
import org.col.db.mapper.InitMybatisRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TreeResourceTest extends ResourceTestBase {
  
  static GenericType<ResultPage<TreeNode>> RESP_TYPE = new GenericType<ResultPage<TreeNode>>() {};
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple(RULE.getSqlSessionFactory());
  
  public TreeResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void get() {
    ResultPage<TreeNode> root = base.path("/11/tree").request().get(RESP_TYPE);
    assertEquals(2, root.size());
    // make sure we get the html markup
    assertEquals("<i>Larus</i> <i>fuscus</i>", root.getResult().get(0).getName());
  }
  
}