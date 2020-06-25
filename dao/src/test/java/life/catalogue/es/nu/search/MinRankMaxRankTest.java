package life.catalogue.es.nu.search;

import java.util.List;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Test;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;
import static org.junit.Assert.assertEquals;

public class MinRankMaxRankTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setMinRank(Rank.KINGDOM);
    index(testData());
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(1, result.size());
    assertEquals("AAA", result.get(0).getId());
  }

  @Test
  public void test2() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setMinRank(Rank.PHYLUM);
    query.setMaxRank(Rank.PHYLUM);
    index(testData());
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(1, result.size());
    assertEquals("BBB", result.get(0).getId());
  }

  @Test
  public void test3() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setMinRank(Rank.SUBGENUS);
    query.setMaxRank(Rank.SUPERPHYLUM);
    index(testData());
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(2, result.size());
    assertEquals("BBB", result.get(0).getId());
    assertEquals("CCC", result.get(1).getId());
  }

  @Test
  public void test4() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setMinRank(Rank.SUPERPHYLUM);
    query.setMaxRank(Rank.SUBGENUS);
    index(testData());
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(0, result.size());
  }

  @Test
  public void test5() {
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    query.setMaxRank(Rank.KINGDOM);
    index(testData());
    List<NameUsageWrapper> result = search(query).getResult();
    assertEquals(5, result.size());
  }

  private List<NameUsageWrapper> testData() {
    List<NameUsageWrapper> data = createNameUsages(5);
    data.get(0).setId("AAA");
    data.get(0).getUsage().getName().setRank(Rank.KINGDOM);
    data.get(1).setId("BBB");
    data.get(1).getUsage().getName().setRank(Rank.PHYLUM);
    data.get(2).setId("CCC");
    data.get(2).getUsage().getName().setRank(Rank.GENUS);
    data.get(3).setId("DDD");
    data.get(3).getUsage().getName().setRank(Rank.SPECIES);
    data.get(4).setId("EEE");
    data.get(4).getUsage().getName().setRank(Rank.SUBSPECIES);
    return data;
  }

}
