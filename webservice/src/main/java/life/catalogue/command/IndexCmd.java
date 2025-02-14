package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;

import java.io.BufferedReader;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class IndexCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(IndexCmd.class);

  private static final String ARG_FILE = "file";
  private static final String ARG_KEY = "key";
  private static final String ARG_ALL = "all";
  private static final String ARG_KEY_IGNORE = "ignore";
  private static final String ARG_CREATE = "create";
  private static final String ARG_THREADS = "t";

  public IndexCmd() {
    super("index", "Re-indexes all datasets into Elasticsearch");

  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("--"+ ARG_FILE)
      .dest(ARG_FILE)
      .type(File.class)
      .required(false)
      .help("File with dataset keys to index");
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
      .help("index all datasets into a new index by date");
    subparser.addArgument("--"+ ARG_KEY_IGNORE, "-i")
       .dest(ARG_KEY_IGNORE)
       .nargs("*")
       .type(Integer.class)
       .required(false)
       .help("Dataset key to be excluded from full indexing");
    subparser.addArgument("--"+ ARG_CREATE)
      .dest(ARG_CREATE)
      .type(boolean.class)
      .required(false)
      .setDefault(false)
      .help("create a new index by date");
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of indexing threads to use. Override config entry esConfig.indexingThreads");
  }

  public void prePromt(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg){
    if (namespace.getBoolean(ARG_ALL) || namespace.getBoolean(ARG_CREATE)) {
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
    boolean create = namespace.getBoolean(ARG_CREATE) || namespace.getBoolean(ARG_ALL);
    String index =  create ? indexNameToday() : cfg.es.nameUsage.name;
    return String.format("Indexing DB %s on %s into %sES index %s on %s.\n", cfg.db.database, cfg.db.host, create ? "new " : "", index, cfg.es.hosts);
  }

  @Override
  public void execute() throws Exception {
    try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
      NameUsageIndexService svc = new NameUsageIndexServiceEs(esClient, cfg.es, cfg.normalizer.scratchDir("cli-es-tmp"), factory);
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

      } else if (ns.get(ARG_FILE) != null) {
        File file = ns.get(ARG_FILE);
        List<Integer> keys;
        try (BufferedReader br = UTF8IoUtils.readerFromFile(file)) {
          keys = br.lines()
            .filter(line -> !StringUtils.isBlank(line))
            .map(line -> Integer.valueOf(line.trim()))
            .collect(Collectors.toUnmodifiableList());
        }
        LOG.info("Found {} datasets for indexing listed in {}", keys.size(), file.getAbsolutePath());
        svc.indexDatasets(keys);

      } else if (ns.getList(ARG_KEY) != null) {
        List<Integer> keys = ns.getList(ARG_KEY);
        svc.indexDatasets(keys);

      } else {
        System.out.println("No indexing argument given. See help for options");
      }
    }
  }
}
