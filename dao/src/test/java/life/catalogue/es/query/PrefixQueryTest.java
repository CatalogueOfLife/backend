package life.catalogue.es.query;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import static org.junit.Assert.assertEquals;

public class PrefixQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed using the IGNORE_CASE analyzer.
    doc.setAuthorshipComplete(s);
    indexRaw(doc);
    Query query = new CaseInsensitivePrefixQuery("authorshipComplete", s.substring(0, 8));
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test1a() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed using the IGNORE_CASE analyzer.
    doc.setAuthorshipComplete(s);
    indexRaw(doc);
    Query query = new BoolQuery().must(new CaseInsensitivePrefixQuery("authorshipComplete", s.substring(0, 8)));
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test2() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed as-is.
    doc.setUsageId(s);
    indexRaw(doc);
    Query query = new PrefixQuery("usageId", s);
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void test3() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed as-is.
    doc.setUsageId(s);
    indexRaw(doc);
    Query query = new PrefixQuery("usageId", s.substring(0, 10));
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(1, result.size());
  }

}
