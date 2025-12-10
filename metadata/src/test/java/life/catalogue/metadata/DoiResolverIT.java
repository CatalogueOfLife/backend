package life.catalogue.metadata;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.Issue;

import java.io.IOException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.*;

@Disabled("The underlying crossref service is rather unreliable")
public class DoiResolverIT {

  static CloseableHttpClient http;
  DoiResolver resolver;

  @BeforeClass
  public static void init(){
    http = HttpClientBuilder.create().build();
  }

  @AfterClass
  public static void destroy() throws IOException {
    http.close();
  }

  @Before
  public void initTest(){
    resolver = new DoiResolver(http);
  }

  @Test
  public void fixError() throws Exception {
    DOI doi = new DOI("10.1007/978-3-030-99742-7");
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    assertEquals(doi, cit.getDoi());
    assertEquals(doi.getDoiName(), cit.getId());
    assertEquals("Systematics, Evolution, and Ecology of Melastomataceae", cit.getTitle());
    assertNull(cit.getContainerTitle());
    assertEquals("9783030997410", cit.getIsbn());
    assertEquals("Springer International Publishing", cit.getPublisher());
    assertEquals(CSLType.BOOK, cit.getType());
    assertEquals("2022", cit.getIssued().toString());
    assertFalse(issues.contains(Issue.DOI_NOT_FOUND));

    // test other so far failed DOIs with weird mixes of arrays and simple strings
    assertWorks("10.1007/978-3-030-73943-0_4");
    assertWorks("10.1051/978-2-7598-2910-1");
    assertWorks("10.3372/cubalist.2016.1");
    assertWorks("10.15393/j4.journal");
  }

  void assertWorks(String doi) {
    var doi2 = new DOI(doi);
    var issues = IssueContainer.simple();
    var cit = resolver.resolve(doi2, issues);
    assertNotNull(cit);
    assertNotNull(cit.getType());
    assertNotNull(cit.getTitle());
  }
  @Test
  public void crossref() throws Exception {
    DOI doi = new DOI("10.1007/s00705-021-05156-1");
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    assertEquals(doi, cit.getDoi());
    assertEquals(doi.getDoiName(), cit.getId());
    assertEquals("Archives of Virology", cit.getContainerTitle());
    assertEquals(CSLType.ARTICLE_JOURNAL, cit.getType());
    assertNotNull(cit.getAuthor().get(0).getOrcid());
    assertFalse(issues.contains(Issue.DOI_NOT_FOUND));
  }

  @Test
  public void notExisting() throws Exception {
    var resolver = new DoiResolver(http);
    DOI doi = new DOI("10.1007/s007051234567wertzucvbnmxcvbnm");
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    assertNull(cit);
    assertTrue(issues.contains(Issue.DOI_NOT_FOUND));
  }

}