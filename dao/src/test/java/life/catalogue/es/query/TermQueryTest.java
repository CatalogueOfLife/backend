package life.catalogue.es.query;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TermQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    EsNameUsage doc1 = build(1, TaxonomicStatus.BARE_NAME);
    EsNameUsage doc2 = build(2, TaxonomicStatus.ACCEPTED);
    EsNameUsage doc3 = build(3, TaxonomicStatus.ACCEPTED);
    EsNameUsage doc4 = build(3, TaxonomicStatus.ACCEPTED);
    EsNameUsage doc5 = build(3, TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    EsNameUsage doc6 = build(6, TaxonomicStatus.SYNONYM);
    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);

    List<EsNameUsage> result = queryRaw(new TermQuery("datasetKey", 3));
    assertEquals(3, result.size());

    result = queryRaw(new TermQuery("status", TaxonomicStatus.ACCEPTED));
    assertEquals(3, result.size());

    result = queryRaw(new TermQuery("status", TaxonomicStatus.BARE_NAME));
    assertEquals(1, result.size());
  }

  static EsNameUsage build(int datasetKey, TaxonomicStatus status){
    EsNameUsage doc = new EsNameUsage();
    doc.setDatasetKey(datasetKey);
    doc.setStatus(status);
    return doc;
  }

}
