package org.col.es.query;

import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.col.es.IndexConfig;
import org.junit.BeforeClass;
import org.junit.Test;

public class QueryTest {

  // private static final Logger LOG = LoggerFactory.getLogger(QueryTest.class);

  private static final IndexConfig cfg = new IndexConfig();

  @BeforeClass
  public static void init() {
    cfg.modelClass = "org.col.es.model.EsNameUsage";
  }

  @Test
  public void testConstantScore() {
    EsSearchRequest esr = new EsSearchRequest();
    ConstantScoreQuery csq = new ConstantScoreQuery(new TermQuery("genus", "Parus", 8.1f));
    esr.setQuery(csq);
    System.out.println(serialize(esr));
  }

  @Test
  public void testIsNotNull() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNotNullQuery("genus"));
    System.out.println(serialize(esr));
  }

  @Test
  public void testIsNull() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new IsNullQuery("genus"));
    System.out.println(serialize(esr));
  }

  @Test
  public void testBool() {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().must(new IsNullQuery("genus"))
        .must(new NGramQuery("Parus", 2))
        .must(new AutoCompleteQuery("area", "Amsterdam"))
        .mustNot(new TermQuery("date", LocalDate.now()));
    esr.setQuery(bq);
    System.out.println(serialize(esr));
  }

  @Test
  public void testTerms() {
    EsSearchRequest esr = new EsSearchRequest();
    BoolQuery bq = new BoolQuery().should(new TermsQuery("age", 1, 2, 3, 4, 5))
        .should(new TermsQuery("genus", "a", "b", "c", "d", "e"));
    esr.setQuery(bq);
    System.out.println(serialize(esr));
  }

  @Test
  public void testPrefix() {
    EsSearchRequest esr = new EsSearchRequest();
    esr.setQuery(new PrefixQuery("genus", "Parus"));
    System.out.println(serialize(esr));
  }

  private static String serialize(Object obj) {
    try {
      return cfg.getQueryWriter().withDefaultPrettyPrinter().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
