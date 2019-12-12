package life.catalogue.command.es;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.db.MybatisFactory;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.name.index.NameUsageIndexService;
import life.catalogue.es.name.index.NameUsageIndexServiceEs;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class IndexCmd extends ConfiguredCommand<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(IndexCmd.class);

  private static final String ARG_KEY = "key";
  private static final String ARG_ALL = "all";

  public IndexCmd() {
    super("index", "Re-indexes all datasets into Elasticsearch");

  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("--"+ ARG_KEY, "-k")
            .dest(ARG_KEY)
            .nargs("*")
            .type(Integer.class)
            .required(false)
            .help("Dataset key to index");
    subparser.addArgument("--"+ ARG_ALL)
            .dest(ARG_ALL)
            .type(boolean.class)
            .required(false)
            .setDefault(false)
            .help("index all datasets if present");
  }

  @Override
  protected void run(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
      try (HikariDataSource dataSource = cfg.db.pool()) {
        SqlSessionFactory factory = MybatisFactory.configure(dataSource, "indexAllCmd");
        NameUsageIndexService svc = new NameUsageIndexServiceEs(esClient, cfg.es, factory);

        if (namespace.getBoolean(ARG_ALL)) {
          svc.indexAll();

        } else if(namespace.getList(ARG_KEY) != null) {
          List<Integer> keys = namespace.getList(ARG_KEY);
          keys.forEach(svc::indexDataset);

        } else {
          System.out.println("No indexing argument given. See help for options");
        }
      }
    }
  }

}
