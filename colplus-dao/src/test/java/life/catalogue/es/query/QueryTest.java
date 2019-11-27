package life.catalogue.es.query;

import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.es.EsModule;
import org.junit.Before;
import org.junit.Test;

/*
 * Not real tests here; just to see whether or not the various types of queries are serialized as expected.
 */
public class QueryTest {

  @Before
  public void before() {
    System.out.println();
    System.out.println("------------------------------------------------------------");
    System.out.println();
  }

  @Test
  public void testConstantScore() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    ConstantScoreQuery csq = new ConstantScoreQuery(new TermQuery("genus", "Parus").withBoost(8.1));
    esr.setQuery(csq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testIsNotNull() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNotNullQuery("genus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testIsNull() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNullQuery("genus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testBool() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().must(new IsNullQuery("genus"))
        .must(new AutoCompleteQuery("area", "Amsterdam"))
        .mustNot(new TermQuery("date", LocalDate.now()));
    esr.setQuery(bq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testTerms() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().should(new TermsQuery("age", 1, 2, 3, 4, 5))
        .should(new TermsQuery("genus", "a", "b", "c", "d", "e"));
    esr.setQuery(bq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testPrefix() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new PrefixQuery("genus", "Parus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testMatchAll() throws JsonProcessingException {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new MatchAllQuery());
    System.out.println(EsModule.writeDebug(esr));
  }

}
