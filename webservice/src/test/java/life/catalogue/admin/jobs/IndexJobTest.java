package life.catalogue.admin.jobs;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.es.EsSetupRule;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.es.nu.search.NameUsageSearchServiceEs;
import life.catalogue.es.nu.suggest.NameUsageSuggestionServiceEs;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Indexes a dataset from postgres into elastic.
 * Does not wipe or import into the database.
 * This is indexing only!
 */
@Ignore
public class IndexJobTest {
  static User user;
  NameUsageIndexService indexService;
  NameUsageSearchServiceEs searchService;
  NameUsageSuggestionServiceEs suggestService;
  IndexJob job;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule(false);

  @ClassRule
  public static final EsSetupRule esSetupRule = new EsSetupRule();

  @Before
  public void init(){
    user = new User();
    user.setKey(Users.TESTER);
    user.setFirstname("Tim");
    user.setLastname("Tester");
    indexService = new NameUsageIndexServiceEs(esSetupRule.getClient(), esSetupRule.getEsConfig(), PgSetupRule.getSqlSessionFactory());
    searchService = new NameUsageSearchServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
    suggestService = new NameUsageSuggestionServiceEs(esSetupRule.getEsConfig().nameUsage.name, esSetupRule.getClient());
  }

  @Test
  public void indexDataset() {
    RequestScope req = new RequestScope();
    req.setDatasetKey(1000);
    job = new IndexJob(req, user, null, indexService);
    job.run();
    System.out.println("INDEX JOB DONE !!!");
  }

  @Test
  public void search() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 2052);
    req.setQ("kosterini");
    req.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    search(req);
  }

  NameUsageSearchResponse search(NameUsageSearchRequest req){
    NameUsageSearchResponse resp = searchService.search(req, new Page(0,100));
    System.out.println("\n-----\n");
    System.out.println(String.format("%s results:", resp.size()));
    for (NameUsageWrapper nuw : resp) {
      System.out.println(String.format("%s %s %s", nuw.getId(), nuw.getUsage().getRank(), nuw.getUsage().getLabel()));
    }
    return resp;
  }
}