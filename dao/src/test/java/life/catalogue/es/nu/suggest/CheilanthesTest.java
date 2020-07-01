package life.catalogue.es.nu.suggest;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.api.search.NameUsageSuggestResponse;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsTestUtils;

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
    query.setQ("Cheilanthes");
    query.setFuzzy(true);
    NameUsageSuggestResponse nur = suggest(query);
  }

}
