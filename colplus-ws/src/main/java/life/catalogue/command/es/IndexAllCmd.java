package life.catalogue.command.es;

import com.zaxxer.hikari.HikariDataSource;

import net.sourceforge.argparse4j.inf.Namespace;

import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.WsServerConfig;
import life.catalogue.db.MybatisFactory;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.name.index.NameUsageIndexService;
import life.catalogue.es.name.index.NameUsageIndexServiceEs;
import org.elasticsearch.client.RestClient;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;

public class IndexAllCmd extends ConfiguredCommand<WsServerConfig> {

  public IndexAllCmd() {
    super("index-all", "Re-indexes all datasets into Elasticsearch");

  }

  @Override
  protected void run(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
      try (HikariDataSource dataSource = cfg.db.pool()) {
        SqlSessionFactory factory = MybatisFactory.configure(dataSource, "es_index");
        NameUsageIndexService svc = new NameUsageIndexServiceEs(esClient, cfg.es, factory);
        svc.indexAll();
      }
    }
  }

}
