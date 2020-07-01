package life.catalogue.es.nu.suggest;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsTestUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class CheilanthesTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test01() throws IOException {
    EsTestUtils.indexCheilanthes(this);
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(3);
    query.setQ("Cheilant");
    query.setFuzzy(true);
    NameUsageSuggestResponse nur = suggest(query);
    assertEquals(1, nur.getSuggestions().size());
  }

  @Test
  public void test02() throws IOException {
    EsTestUtils.indexCheilanthes(this);
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetKey(3);
    query.setQ("Che");
    query.setFuzzy(false);
    NameUsageSuggestResponse nur = suggest(query);
    assertEquals(1, nur.getSuggestions().size());
  }

}
