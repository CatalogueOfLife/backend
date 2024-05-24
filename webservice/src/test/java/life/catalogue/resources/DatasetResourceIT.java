package life.catalogue.resources;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.db.TestDataRule;

import java.util.List;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.ApiUtils.*;
import static life.catalogue.api.TestEntityGenerator.nullifyUserDate;
import static org.junit.Assert.*;

public class DatasetResourceIT extends ResourceITBase {
  
  static GenericType<ResultPage<Dataset>> RESULT_PAGE = new GenericType<ResultPage<Dataset>>() {};
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.datasetMix();

  public DatasetResourceIT() {
    super("/dataset");
  }


  @Test
  public void list() {
    Page page = new Page(0,10);
    ResultPage<Dataset> resp = userCreds(applyPage(base, page)).get(RESULT_PAGE);

    // 7 public, 2 private ones
    assertEquals(7, resp.size());
    for (Dataset d : resp) {
      assertNotNull(d);
    }
  
    DatasetSearchRequest req = DatasetSearchRequest.byQuery("My project");
    req.setSortBy(DatasetSearchRequest.SortBy.TITLE);
    resp = userCreds(applySearch(base, req, page)).get(RESULT_PAGE);
  
    assertEquals(4, resp.size());
    assertEquals("My project", resp.getResult().get(0).getTitle());
  
    req.setLicense(List.of(License.CC0));
    resp = userCreds(applySearch(base, req, page)).get(RESULT_PAGE);
    assertTrue(resp.isEmpty());
  }
  
  @Test
  public void create() {
    Dataset d = new Dataset();
    d.setTitle("s3s3derftg");
    d.setType(DatasetType.OTHER);
    d.setOrigin(DatasetOrigin.EXTERNAL);
    d.setContact(Agent.parse("me"));
    d.setIssued(FuzzyDate.now());
    d.setVersion("1.23");
    Integer key = editorCreds(base).post(json(d), Integer.class);
    d.setKey(key);
    
    Dataset d2 = userCreds(base.path(key.toString())).get(Dataset.class);
    // new datasets are created as private ones
    assertTrue(d2.isPrivat());

    d2.setPrivat(false);
    assertEquals(nullifyVolatile(d2), nullifyVolatile(d));
  }

  static Dataset nullifyVolatile(Dataset d) {
    nullifyUserDate(d);
    return d;
  }

  @Test
  public void get() {
    Dataset d = userCreds(base.path("1008")).get(Dataset.class);
    assertNotNull(d);
    assertNull(d.getDeleted());
    assertEquals("The Reptile Database", d.getTitle());
  }
  
  @Test
  @Ignore
  public void update() {
  }
  
  @Test
  public void delete() {
    Response resp = userCreds(base.path("1008")).delete();
    // no permission!
    assertEquals(403, resp.getStatus());

    addUserPermission("user", 1008);

    resp = userCreds(base.path("1008")).delete();
    assertEquals(204, resp.getStatus());

    Dataset d = userCreds(base.path("1008")).get(Dataset.class);
    assertNotNull(d.getDeleted());
  }

}