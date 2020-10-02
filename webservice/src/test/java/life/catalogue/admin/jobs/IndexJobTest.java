package life.catalogue.admin.jobs;

import com.zaxxer.hikari.HikariDataSource;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.RequestScope;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.MybatisFactory;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.IndexConfig;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Use with care !!!
 * Only for debugging dev with real data remotely
 */
@Ignore
public class IndexJobTest {
  static WsServerConfig cfg;
  static User user;
  static NameUsageIndexService indexService;
  static RestClient esClient;
  static SqlSessionFactory factory;
  static HikariDataSource dataSource;
  IndexJob job;

  @BeforeClass
  public static void init(){
    cfg = new WsServerConfig();
    cfg.db.host = "pg1.dev.catalogue.life";
    cfg.db.database = "coldev";
    cfg.db.user = "col";
    cfg.db.password = "";
    cfg.es.hosts = cfg.db.host;
    cfg.es.nameUsage = new IndexConfig();
    cfg.es.nameUsage.name = "dev-nu";

    user = new User();
    user.setKey(Users.TESTER);
    user.setFirstname("Tim");
    user.setLastname("Tester");
    dataSource = new HikariDataSource(cfg.db.hikariConfig());
    factory = MybatisFactory.configure(dataSource, IndexJobTest.class.getSimpleName());

    esClient = new EsClientFactory(cfg.es).createClient();
    indexService = new NameUsageIndexServiceEs(esClient, cfg.es, factory);
  }

  @AfterClass
  public static void destroy() throws Exception {
    dataSource.close();
    esClient.close();
  }

  @Test
  public void indexDataset() {
    RequestScope req = new RequestScope();
    req.setDatasetKey(1106);
    job = new IndexJob(req, user, null, indexService);
    job.run();
    System.out.println("INDEX JOB DONE !!!");
  }
}