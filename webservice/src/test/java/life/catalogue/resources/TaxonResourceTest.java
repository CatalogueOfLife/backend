package life.catalogue.resources;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.TestDataRule;

import javax.ws.rs.ForbiddenException;

import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.ApiUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TaxonResourceTest extends ResourceTestBase {
  private final int datasetKey = TestEntityGenerator.DATASET11.getKey();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple(RULE.getSqlSessionFactory());
  
  public TaxonResourceTest() {
    super("/dataset/11/taxon");
  }
  
  @Test
  public void get() {
    Taxon t = userCreds(base.path("root-1")).get(Taxon.class);
    assertNotNull(t);
    assertEquals("root-1", t.getId());
  }
  
  @Test
  public void create() throws Exception {
    RULE.startNamesIndex();
    Taxon t = createTaxon();
    t.setId( adminCreds(base).post(json(t), String.class) );
    t.getName().setNamesIndexId(5);
    t.getName().setNamesIndexType(MatchType.EXACT);

    Taxon t2 = userCreds(base.path(t.getId())).get(Taxon.class);
    assertNotNull(t2);

    // manually created taxa will always be of origin USER
    assertEquals(t.getId(), t2.getId());

    TestEntityGenerator.nullifyUserDate(t2);


    prepareEquals(t);
    prepareEquals(t2);
    t2.getName().setId(null);

    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(t, t2);
    System.out.println(diff);

    assertEquals(t, t2);
  }

  private void prepareEquals(Taxon t) {
    t.getName().setOrigin(Origin.USER);
    t.setOrigin(Origin.USER);
    TestEntityGenerator.nullifyUserDate(t);
  }

  private Taxon createTaxon() {
    Taxon t = TestEntityGenerator.newTaxon(datasetKey);
    t.setId(null);
    t.getName().setId(null);
    t.setParentId("root-1");
    return t;
  }

  @Test(expected = ForbiddenException.class)
  public void createFail() {
    Taxon t = createTaxon();
    editorCreds(base).post(json(t), String.class);
  }
  
}