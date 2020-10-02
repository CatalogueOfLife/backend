package life.catalogue.es.query;

import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PrefixQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void author() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed using the IGNORE_CASE analyzer.
    doc.setAuthorshipComplete(s);
    indexRaw(doc);
    Query query = new StandardAsciiQuery("authorshipComplete", "LoNg");
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(1, result.size());
  }

  @Test
  public void authorNoMatch() {
    EsNameUsage doc = new EsNameUsage();
    String s = "  tHiS  iS  a  LoNg  string  with  spaces";
    // This field is indexed using the IGNORE_CASE analyzer.
    doc.setAuthorshipComplete(s);
    indexRaw(doc);
    Query query = new BoolQuery().must(new StandardAsciiQuery("authorshipComplete", "LoN"));
    List<EsNameUsage> result = queryRaw(query);
    assertEquals(0, result.size());
  }

  @Test
  public void usageId() {
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
  public void usageId2() {
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
