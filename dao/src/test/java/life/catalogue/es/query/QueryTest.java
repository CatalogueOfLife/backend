package life.catalogue.es.query;

import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.es.EsModule;

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
  public void testConstantScore() {
    EsSearchRequest esr = new EsSearchRequest();
    ConstantScoreQuery csq = new ConstantScoreQuery(new TermQuery("genus", "Parus").withBoost(8.1));
    esr.setQuery(csq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testIsNotNull() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNotNullQuery("genus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testIsNull() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNullQuery("genus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testBool() {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().must(new IsNullQuery("genus"))
        .must(new EdgeNgramQuery("area", "Amsterdam"))
        .mustNot(new TermQuery("date", LocalDate.now()));
    esr.setQuery(bq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testTerms() {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().should(new TermsQuery("age", 1, 2, 3, 4, 5))
        .should(new TermsQuery("genus", "a", "b", "c", "d", "e"));
    esr.setQuery(bq);
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testPrefix() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new PrefixQuery("genus", "Parus"));
    System.out.println(EsModule.writeDebug(esr));
  }

  @Test
  public void testMatchAll() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new MatchAllQuery());
    System.out.println(EsModule.writeDebug(esr));
  }

}
