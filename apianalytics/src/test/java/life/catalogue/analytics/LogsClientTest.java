package life.catalogue.analytics;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.dw.jersey.filter.DatasetKeyRewriteFilter;
import life.catalogue.es.EsConfig;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("mamual test only")
public class LogsClientTest {

  @ClassRule
  public static SqlSessionFactoryRule pgRule = new PgSetupRule();

  @Test
  public void test() throws Exception {
    EsConfig cfg = new EsConfig();
    cfg.hosts="private-logs.gbif.org";
    cfg.ports="9200";

    var range  = Duration.ofHours(1);

    try (var client = new LogsClient(cfg)) {
      var dao = new ApiAnalyticsDao(client, PgSetupRule.getSqlSessionFactory(), new DatasetKeyRewriteFilter(LatestDatasetKeyCache.passThru()));
      LocalDateTime end = dao.getCurrentHour();
      LocalDateTime start = end.minus(Duration.ofHours(5));
      dao.createAnalyticsRange(start, end, range);
    }
  }
}