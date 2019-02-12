package org.col.admin.command.es;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.AdminServerConfig;
import org.col.db.MybatisFactory;
import org.col.es.EsClientFactory;
import org.col.es.NameUsageIndexService;
import org.col.es.NameUsageIndexServiceEs;
import org.elasticsearch.client.RestClient;

public class IndexAllCmd extends ConfiguredCommand<AdminServerConfig> {

  public IndexAllCmd() {
    super("index-all", "Re-indexes all datasets into Elasticsearch");

  }

  @Override
  protected void run(Bootstrap<AdminServerConfig> bootstrap, Namespace namespace, AdminServerConfig cfg) throws Exception {
    try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
      try (HikariDataSource dataSource = cfg.db.pool()) {
        SqlSessionFactory factory = MybatisFactory.configure(dataSource, "es_index");
        NameUsageIndexService svc = new NameUsageIndexServiceEs(esClient, cfg.es, factory);
        svc.indexAll();
      }
    }
  }

}
