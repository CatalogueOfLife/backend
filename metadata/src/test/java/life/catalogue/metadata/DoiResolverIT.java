package life.catalogue.metadata;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.vocab.Issue;

import java.io.IOException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

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
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    assertEquals(doi, cit.getDoi());
    assertEquals(doi.getDoiName(), cit.getId());
    assertEquals("Archives of Virology", cit.getContainerTitle());
    assertEquals(CSLType.ARTICLE_JOURNAL, cit.getType());
    assertNotNull(cit.getAuthor().get(0).getOrcid());
    assertFalse(issues.hasIssue(Issue.DOI_NOT_FOUND));
  }

  @Test
  public void notExisting() throws Exception {
    var resolver = new DoiResolver(http);
    DOI doi = new DOI("10.1007/s007051234567wertzucvbnmxcvbnm");
    IssueContainer issues = IssueContainer.simple();
    var cit = resolver.resolve(doi, issues);
    assertNull(cit);
    assertTrue(issues.hasIssue(Issue.DOI_NOT_FOUND));
  }

}