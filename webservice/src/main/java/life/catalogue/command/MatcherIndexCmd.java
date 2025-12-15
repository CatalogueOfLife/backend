package life.catalogue.command;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.WsMatchingServerConfig;
import life.catalogue.WsServerConfig;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.metadata.coldp.DatasetJsonWriter;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;

import org.gbif.api.ws.mixin.DatasetMixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.jackson.Jackson;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

public class MatcherIndexCmd extends ConfiguredCommand<WsMatchingServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(MatcherIndexCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_DIR = "dir";
  private static final String ARG_DELETE = "delete";
  private File buildDir;

  public MatcherIndexCmd() {
    super("matcher", "Rebuilt new matching and names index for a given dataset to support a WsMatchingServer");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false);
    subparser.addArgument("--"+ ARG_DIR)
       .dest(ARG_DIR)
       .type(String.class)
       .required(false);
    subparser.addArgument("--"+ ARG_DELETE)
      .dest(ARG_DELETE)
      .type(boolean.class)
      .required(false)
      .setDefault(false);
  }

  @Override
  protected void run(Bootstrap<WsMatchingServerConfig> bootstrap, Namespace ns, WsMatchingServerConfig cfg) throws Exception {
    System.out.println( String.format("Rebuilt new matching and names index for dataset %s in db %s on %s to support a WsMatchingServer.", ns.getInt(ARG_KEY), cfg.db.database, cfg.db.host));

    // prepare
    // use a custom jackson mapper
    ObjectMapper om = ApiModule.configureMapper(Jackson.newMinimalObjectMapper());
    bootstrap.setObjectMapper(om);
    // files n configs
    boolean delete = ns.getBoolean(ARG_DELETE);
    var custumDir = ns.getString(ARG_DIR);
    if (custumDir != null) {
      buildDir = new File(custumDir);
      cfg.matching.storageDir = buildDir;
    } else {
      buildDir = cfg.matching.storageDir;
    }
    cfg.namesIndex.verification = false;
    cfg.namesIndex.file = new File(buildDir, "nidx");
    System.out.println("Build new index at " + buildDir.getAbsolutePath());
    if (!buildDir.exists()) {
      buildDir.mkdirs();
    } else if (delete) {
      System.out.println("Build dir already exists. Delete entire previous content");
      FileUtils.deleteDirectory(buildDir);
    } else {
      System.out.println("Build dir already exists.");
    }
    if (cfg.namesIndex.file.exists()) {
      System.out.println("Names index already exists, please use the --delete parameter or remove manually: " + cfg.namesIndex.file.getAbsolutePath());
      System.exit(1);
    }
    // key
    final int key = ObjectUtils.coalesce(ns.getInt(ARG_KEY), cfg.matchingDatasetKey);
    final File dir = cfg.matching.dir(key);
    System.out.println("Index dataset " + key);
    if (dir.exists()) {
      System.out.println("Matcher index directory already exists, please use the --delete parameter or remove manually: " + dir.getAbsolutePath());
      System.exit(1);
    }

    // mybatis
    LOG.info("Connecting to database {} on {}", cfg.db.database, cfg.db.host);
    try (var dataSource = cfg.db.pool()) {
      var factory = MybatisFactory.configure(dataSource, getClass().getSimpleName());
      DatasetInfoCache.CACHE.setFactory(factory);

      // DO WORK NOW !!!
      // dataset json
      try (SqlSession session = factory.openSession()) {
        var d = session.getMapper(DatasetMapper.class).get(key);
        if (d.getSize() == null || d.getSize() < 1) {
          int cnt = session.getMapper(NameUsageMapper.class).count(key);
          d.setSize(cnt);
        }
        System.out.println("Index dataset " + key + " " + d.getTitle());
        File df = cfg.matching.datasetJson(key);
        DatasetJsonWriter.write(d, df);
      }
      // names index
      final NameIndex ni = NameIndexFactory.build(cfg.namesIndex, null, AuthorshipNormalizer.INSTANCE);
      ni.start();
      // matching index
      UsageMatcher m;
      try (SqlSession s = factory.openSession()) {
        var um = s.getMapper(NameUsageMapper.class);
        int count = 1000 + um.count(key);
        var samples = um.listSN(key, new Page(0,10));
        m = UsageMatcherFactory.buildPersistentMatcher(key, samples, count, cfg.matching, ni);
        m.load(factory, ni);
      }
      // orderly shutdown
      m.close();
      ni.stop();
      LOG.info("Done building matching index for dataset {} at {}", key, buildDir.getAbsolutePath());
      System.out.println("Done !!!");
    }
  }
}
