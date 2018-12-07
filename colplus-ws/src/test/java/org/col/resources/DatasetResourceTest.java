package org.col.resources;

import java.time.LocalDate;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.google.common.collect.Sets;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.api.vocab.Catalogue;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DatasetOrigin;
import org.col.api.vocab.Frequency;
import org.col.db.mapper.InitMybatisRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.nullifyUserDate;
import static org.col.dw.ApiUtils.*;
import static org.junit.Assert.*;

public class DatasetResourceTest extends ResourceTestBase {
  
  static GenericType<ResultPage<Dataset>> RESULT_PAGE = new GenericType<ResultPage<Dataset>>() {};
  
  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.datasets(RULE.getSqlSessionFactory());

  public DatasetResourceTest() {
    super("/dataset");
  }
  
  @Test
  public void list() {
    Page page = new Page(0,10);
    ResultPage<Dataset> resp = userCreds(applyPage(base, page)).get(RESULT_PAGE);
    
    assertEquals(10, resp.size());
    assertTrue(resp.getTotal() > 200);
    for (Dataset d : resp) {
      assertNotNull(d);
    }
  
    DatasetSearchRequest req = DatasetSearchRequest.byQuery("Catalogue");
    req.setSortBy(DatasetSearchRequest.SortBy.TITLE);
    resp = userCreds(applySearch(base, req, page)).get(RESULT_PAGE);
  
    assertEquals(8, resp.size());
    assertEquals("Catalogue of Afrotropical Bees", resp.getResult().get(0).getTitle());
  
    req.setFormat(DataFormat.DWCA);
    req.setContributesTo(Sets.newHashSet(Catalogue.COL, Catalogue.PCAT));
    resp = userCreds(applySearch(base, req, page)).get(RESULT_PAGE);
  
    assertEquals(5, resp.size());
    assertEquals("Catalogue of Afrotropical Bees", resp.getResult().get(0).getTitle());
  }
  
  @Test
  public void create() {
    Dataset d = new Dataset();
    d.setTitle("s3s3derftg");
    d.setOrigin(DatasetOrigin.UPLOADED);
    d.setContact("me");
    d.setReleased(LocalDate.now());
    d.setImportFrequency(Frequency.MONTHLY);
    Integer key = editorCreds(base).post(json(d), Integer.class);
    d.setKey(key);
    
    Dataset d2 = userCreds(base.path(key.toString())).get(Dataset.class);
    
    assertEquals(nullifyUserDate(d2), nullifyUserDate(d));
  }
  
  @Test
  public void get() {
    Dataset d = userCreds(base.path("2035")).get(Dataset.class);
    assertNotNull(d);
    assertNull(d.getDeleted());
    assertEquals("Catalogue of Afrotropical Bees", d.getTitle());
  }
  
  @Test
  @Ignore
  public void update() {
  }
  
  @Test
  public void delete() {
    Response resp = editorCreds(base.path("2035")).delete();
    assertEquals(204, resp.getStatus());
  
    Dataset d = userCreds(base.path("2035")).get(Dataset.class);
    assertNotNull(d.getDeleted());
  }
  
  
  
  @Test
  @Ignore
  public void getImports() {
  }
  
  @Test
  @Ignore
  public void logo() {
  }
  
  @Test
  @Ignore
  public void uploadLogo() {
  }
  
  @Test
  @Ignore
  public void deleteLogo() {
  }
}