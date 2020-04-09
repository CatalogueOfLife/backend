package life.catalogue.resources;

import javax.ws.rs.core.GenericType;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.TreeNode;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.TestDataRule;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.ApiUtils.editorCreds;
import static life.catalogue.ApiUtils.json;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Ignore("TODO: under construction")
public class TaxonResourceTest extends ResourceTestBase {
  private final int datasetKey = TestEntityGenerator.DATASET11.getKey();
  
  static GenericType<ResultPage<TreeNode>> RESP_TYPE = new GenericType<ResultPage<TreeNode>>() {};
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple(RULE.getSqlSessionFactory());
  
  public TaxonResourceTest() {
    super("/dataset/11/taxon");
  }
  
  @Test
  public void get() {
    Taxon t = base.path("root-1").request().get(Taxon.class);
    assertNotNull(t);
    assertEquals("root-1", t.getId());
  }
  
  @Test
  public void create() {
    Taxon t = TestEntityGenerator.newTaxon(datasetKey, null);
    t.setParentId("root-1");
    // manually created taxa will always be of origin USER
    t.setOrigin(Origin.USER);
    
    t.setId( editorCreds(base).post(json(t), String.class) );
  
    Taxon t2 = base.path(t.getId()).request().get(Taxon.class);
    assertNotNull(t2);
    assertEquals(t.getId(), t2.getId());
    
    TestEntityGenerator.nullifyUserDate(t2);
  
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(t, t2);
    System.out.println(diff);
  
    assertEquals(t, t2);
  }
  
}