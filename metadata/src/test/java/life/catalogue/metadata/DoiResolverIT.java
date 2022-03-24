package life.catalogue.metadata;

import de.undercouch.citeproc.csl.CSLType;

import life.catalogue.api.model.DOI;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

@Ignore("The underlying crossref service is rather unreliable")
public class DoiResolverIT {

  static CloseableHttpClient http;

  @BeforeClass
  public static void init(){
    http = HttpClientBuilder.create().build();
  }

  @AfterClass
  public static void destroy() throws IOException {
    http.close();
  }

  @Test
  public void crossref() throws Exception {
    var resolver = new DoiResolver(http);
    DOI doi = new DOI("10.1007/s00705-021-05156-1");
    var cit = resolver.resolve(doi);
    assertEquals(doi, cit.getDoi());
    assertEquals(doi.getDoiName(), cit.getId());
    assertEquals("Archives of Virology", cit.getContainerTitle());
    assertEquals(CSLType.ARTICLE_JOURNAL, cit.getType());
    assertNotNull(cit.getAuthor().get(0).getOrcid());
  }

  @Test
  public void notExisting() throws Exception {
    var resolver = new DoiResolver(http);
    DOI doi = new DOI("10.1007/s007051234567wertzucvbnmxcvbnm");
    var cit = resolver.resolve(doi);
    assertNull(cit);
  }

}