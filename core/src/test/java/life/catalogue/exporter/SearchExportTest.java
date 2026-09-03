package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.vocab.JobLane;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.JobDao;
import life.catalogue.es.search.NameUsageSearchService;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.util.Set;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SearchExportTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  private SearchExport build(NameUsageSearchRequest req) {
    var cfg = TestConfigs.build();
    return new SearchExport(dataRule.testData.key, req, Users.TESTER, NameUsageSearchService.passThru(),
      SqlSessionFactoryRule.getSqlSessionFactory(), cfg.normalizer, cfg.clbURI);
  }

  /**
   * A search download must never escape the dataset it was requested for, no matter what
   * DATASET_KEY filter the caller supplied.
   */
  @Test
  public void requestIsScopedToThePathDataset() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, 999999);
    req.addFilter(NameUsageSearchParameter.RANK, Rank.SPECIES);

    var job = build(req);
    var scoped = job.getSearchRequest();

    assertEquals(Set.of(dataRule.testData.key), scoped.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
    assertEquals(Set.of(Rank.SPECIES), scoped.getFilterValues(NameUsageSearchParameter.RANK));
    // the caller's request must not have been modified in place
    assertEquals(Set.of(999999), req.getFilterValues(NameUsageSearchParameter.DATASET_KEY));
  }

  /**
   * The search request is what makes a download reproducible, so it has to reach job.params.
   * The UI also identifies these jobs by their name.
   */
  @Test
  public void jobInfoCarriesTheSearchRequest() {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.RANK, Rank.GENUS);
    var job = build(req);

    assertEquals(Set.of(Rank.GENUS),
      ((NameUsageSearchRequest) job.getParams()).getFilterValues(NameUsageSearchParameter.RANK));

    JobInfo info = JobDao.buildInfo(job);
    assertEquals("SearchExport", info.getJob());
    assertEquals(Integer.valueOf(dataRule.testData.key), info.getDatasetKey());
    assertEquals(JobLane.DEFAULT, info.getLane());
  }
}
