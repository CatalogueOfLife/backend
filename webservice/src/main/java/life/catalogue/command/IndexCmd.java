package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class IndexCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(IndexCmd.class);

  private static final String ARG_KEY = "key";
  private static final String ARG_ALL = "all";
  private static final String ARG_KEY_IGNORE = "ignore";
  private static final String ARG_THREADS = "t";

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
            .help("Dataset key to index in situ");
    subparser.addArgument("--"+ ARG_ALL)
            .dest(ARG_ALL)
            .type(boolean.class)
            .required(false)
            .setDefault(false)
            .help("index all datasets into a new index by date");
    subparser.addArgument("--"+ ARG_KEY_IGNORE, "-i")
             .dest(ARG_KEY_IGNORE)
             .nargs("*")
             .type(Integer.class)
             .required(false)
             .help("Dataset key to be excluded from full indexing");
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of indexing threads to use. Override config entry esConfig.indexingThreads");
  }

  public void prePromt(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg){
    if (namespace.getBoolean(ARG_ALL)) {
      // change index name, use current date
      cfg.es.nameUsage.name = indexNameToday();
      System.out.println("Creating new index " + cfg.es.nameUsage.name);
      LOG.info("Creating new index {}", cfg.es.nameUsage.name);
    }
  }

  static String indexNameToday(){
    String date = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
    return "col-" + date;
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    String index = namespace.getBoolean(ARG_ALL) ? indexNameToday() : cfg.es.nameUsage.name;
    return String.format("Indexing DB %s on %s into new ES index %s on %s.\n", cfg.db.database, cfg.db.host, index, cfg.es.hosts);
  }

  @Override
  public void execute() throws Exception {
    try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
      NameUsageIndexService svc = new NameUsageIndexServiceEs(esClient, cfg.es, cfg.normalizer.scratchDir("nuproc"), factory);
      if (ns.getInt(ARG_THREADS) != null) {
        cfg.es.indexingThreads = ns.getInt(ARG_THREADS);
        Preconditions.checkArgument(cfg.es.indexingThreads > 0, "Needs at least one indexing thread");
      }
      if (ns.getBoolean(ARG_ALL)) {
        if (ns.getList(ARG_KEY_IGNORE) != null) {
          List<Integer> ignore = ns.getList(ARG_KEY_IGNORE);
          svc.indexAll(ArrayUtils.toPrimitive(ignore.toArray(new Integer[0])));
        } else {
          svc.indexAll();
        }

      } else if (ns.getList(ARG_KEY) != null) {
        List<Integer> keys = ns.getList(ARG_KEY);
        LOG.info("Start sequential indexing of {} datasets", keys.size());
        Set<String> failed = new HashSet<>();
        for (Integer key : keys) {
          try {
            svc.indexDataset(key);
          } catch (RuntimeException e) {
            failed.add(key.toString());
            LOG.error("Failed to index dataset {}", key, e);
          }
        }
        LOG.info("Finished indexing {} datasets. Failed: {}", keys.size(), String.join(", ", failed));

      } else {
        System.out.println("No indexing argument given. See help for options");
      }
    }
  }

}
