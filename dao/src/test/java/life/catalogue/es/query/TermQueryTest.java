package life.catalogue.es.query;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import static org.junit.Assert.assertEquals;

public class TermQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    EsNameUsage doc1 = new EsNameUsage();
    doc1.setDatasetKey(1);
    EsNameUsage doc2 = new EsNameUsage();
    doc2.setDatasetKey(2);
    EsNameUsage doc3 = new EsNameUsage();
    doc3.setDatasetKey(3);
    EsNameUsage doc4 = new EsNameUsage();
    doc4.setDatasetKey(3);
    EsNameUsage doc5 = new EsNameUsage();
    doc5.setDatasetKey(3);
    EsNameUsage doc6 = new EsNameUsage();
    doc6.setDatasetKey(6);
    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);
    List<EsNameUsage> result = queryRaw(new TermQuery("datasetKey", 3));
    assertEquals(3, result.size());
  }

}
