package life.catalogue.es.nu.suggest;

import life.catalogue.api.search.NameUsageSuggestRequest;
import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.EsTestUtils;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class CheilanthesTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test01() throws IOException {
    EsTestUtils.indexCheilanthes(this);
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(3);
    query.setQ("Cheilant");
    query.setFuzzy(true);
    var nur = suggest(query);
    assertEquals(1, nur.size());

    query.setMinRank(Rank.GENUS);
    nur = suggest(query);
    assertEquals(1, nur.size());

    query.setMinRank(Rank.FAMILY);
    nur = suggest(query);
    assertEquals(0, nur.size());

    query.setMinRank(Rank.GENUS);
    query.setMaxRank(Rank.GENUS);
    nur = suggest(query);
    assertEquals(1, nur.size());

    query.setMinRank(null);
    query.setMaxRank(Rank.SPECIES);
    nur = suggest(query);
    assertEquals(0, nur.size());

    query.setMaxRank(Rank.FAMILY);
    nur = suggest(query);
    assertEquals(1, nur.size());
  }

  @Test
  public void test02() throws IOException {
    EsTestUtils.indexCheilanthes(this);
    NameUsageSuggestRequest query = new NameUsageSuggestRequest();
    query.setDatasetFilter(3);
    query.setQ("Che");
    query.setFuzzy(false);
    var nur = suggest(query);
    assertEquals(1, nur.size());
  }

}
