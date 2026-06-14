package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.es.search.NameUsageSearchService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Rule;

public class SearchExportEmailTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Override
  public BackgroundJob buildJob() {
    var cfg = TestConfigs.build();
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);
    return new SearchExport(dataRule.testData.key, req, Users.TESTER, NameUsageSearchService.passThru(),
      SqlSessionFactoryRule.getSqlSessionFactory(), cfg.normalizer, cfg.clbURI);
  }
}
