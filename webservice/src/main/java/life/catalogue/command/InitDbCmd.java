package life.catalogue.command;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.PathUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.EsUtil;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.matching.DatasetMatcher;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.postgres.IntSerial;
import life.catalogue.postgres.PgCopyUtils;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.NameType;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.function.Function;

/**
 * Command to initialise a new database schema.
 */
public class InitDbCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(InitDbCmd.class);

  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }
  
  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    execute(cfg);
  }
  
  public static void execute(WsServerConfig cfg) throws Exception {
    LOG.info("Starting database initialisation with admin connection {}", cfg.adminDb);
    try (Connection con = cfg.db.connect(cfg.adminDb);
         Statement st = con.createStatement()
    ) {
      LOG.info("Drop existing database {}", cfg.db.database);
      st.execute("DROP DATABASE IF EXISTS \"" + cfg.db.database + "\"");
      
      LOG.info("Create new database {}", cfg.db.database);
      st.execute("CREATE DATABASE  \"" + cfg.db.database + "\"" +
          " WITH ENCODING UTF8 LC_COLLATE 'C' LC_CTYPE 'C' OWNER " + cfg.db.user + " TEMPLATE template0");

      LOG.info("Use UTC timezone for {}", cfg.db.database);
      st.execute("ALTER DATABASE  \"" + cfg.db.database + "\" SET timezone TO 'UTC'");
    }
    
    try (Connection con = cfg.db.connect()) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      // run sql schema
      exec(PgConfig.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
      // add common data
      exec(PgConfig.DATA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATA_FILE));
      LOG.info("Insert known datasets");
      exec(PgConfig.DATASETS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATASETS_FILE));
    }
    
    // cleanup names index
    if (cfg.namesIndexFile != null && cfg.namesIndexFile.exists()) {
      LOG.info("Clear names index at {}", cfg.namesIndexFile.getAbsolutePath());
      if (!cfg.namesIndexFile.delete()) {
        LOG.error("Unable to delete names index at {}", cfg.namesIndexFile.getAbsolutePath());
        throw new IllegalStateException("Unable to delete names index at " + cfg.namesIndexFile.getAbsolutePath());
      }
    }
    
    // clear images, scratch dir & archive repo
    LOG.info("Clear image cache {}", cfg.img.repo);
    PathUtils.cleanDirectory(cfg.img.repo);
  
    LOG.info("Clear scratch dir {}", cfg.normalizer.scratchDir);
    if (cfg.normalizer.scratchDir.exists()) {
      FileUtils.cleanDirectory(cfg.normalizer.scratchDir);
    }
  
    LOG.info("Clear archive repo {}", cfg.normalizer.archiveDir);
    if (cfg.normalizer.archiveDir.exists()) {
      FileUtils.cleanDirectory(cfg.normalizer.archiveDir);
    }
  
    LOG.info("Clear metrics repo {}", cfg.metricsRepo);
    if (cfg.metricsRepo.exists()) {
      FileUtils.cleanDirectory(cfg.metricsRepo);
    }

    // load draft catalogue data
    HikariConfig hikari = cfg.db.hikariConfig();
    try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
      // configure single mybatis session factory
      final SqlSessionFactory factory = MybatisFactory.configure(dataSource, "init");
    
      // add col dataset and partitions
      try (SqlSession session = factory.openSession()) {
        setupColPartition(session);
        session.getMapper(DatasetPartitionMapper.class).createManagedSequences(Datasets.COL);
        session.commit();
      }
    
      try (Connection con = cfg.db.connect()) {
        loadDraftHierarchy(con, factory, cfg);
      } catch (Exception e) {
        LOG.error("Failed to insert initdb data", e);
        throw e;
      }
  
      LOG.info("Update dataset sector counts");
      NameDao nd = new NameDao(factory, NameUsageIndexService.passThru(), NameIndexFactory.passThru());
      new TaxonDao(factory, nd, NameUsageIndexService.passThru()).updateAllSectorCounts(Datasets.COL);
      
      updateSearchIndex(cfg, factory);
    }
  }
  
  private static void loadDraftHierarchy(Connection con, SqlSessionFactory factory, WsServerConfig cfg) throws Exception {
    LOG.info("Insert CoL draft data linked to sectors");
    PgConnection pgc = (PgConnection) con;
    // Use sector exports from Global Assembly:
    // https://github.com/Sp2000/colplus-repo#sector-exports
    PgCopyUtils.copy(pgc, "sector", "/life/catalogue/db/draft/sector.csv", ImmutableMap.<String, Object>builder()
        .put("mode", Sector.Mode.ATTACH)
        .put("dataset_key", Datasets.COL)
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build());
    PgCopyUtils.copy(pgc, "reference_"+Datasets.COL, "/life/catalogue/db/draft/reference.csv", ImmutableMap.<String, Object>builder()
        .put("dataset_key", Datasets.COL)
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build());
    PgCopyUtils.copy(pgc, "estimate", "/life/catalogue/db/draft/estimate.csv", ImmutableMap.<String, Object>builder()
        .put("type", EstimateType.SPECIES_LIVING)
        .put("dataset_key", Datasets.COL)
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build(),
      ImmutableMap.<String, Function<String[], String>>of(
        "id", new IntSerial()
      )
    );
    // id,homotypic_name_id,rank,scientific_name,uninomial
    PgCopyUtils.copy(pgc, "name_"+Datasets.COL, "/life/catalogue/db/draft/name.csv", ImmutableMap.<String, Object>builder()
          .put("dataset_key", Datasets.COL)
          .put("origin", Origin.SOURCE)
          .put("type", NameType.SCIENTIFIC)
          .put("nom_status", NomStatus.ACCEPTABLE)
          .put("created_by", Users.DB_INIT)
          .put("modified_by", Users.DB_INIT)
        .build(),
        ImmutableMap.<String, Function<String[], String>>of(
            "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[3]),
            "authorship_normalized", x -> null
        )
    );
    PgCopyUtils.copy(pgc, "name_usage_"+Datasets.COL, "/life/catalogue/db/draft/taxon.csv", ImmutableMap.<String, Object>builder()
        .put("dataset_key", Datasets.COL)
        .put("origin", Origin.SOURCE)
        .put("status", TaxonomicStatus.ACCEPTED)
        .put("is_synonym", false)
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build());

    LOG.info("Update managed sequence values");
    try (SqlSession session = factory.openSession(true)) {
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);
      dpm.updateManagedSequences(Datasets.COL);
    }

    LOG.info("Match draft CoL to names index");
    // we create a new names index de novo to write new hierarchy names into the names index dataset
    try (NameIndex ni = NameIndexFactory.persistentOrMemory(cfg.namesIndexFile, factory, AuthorshipNormalizer.INSTANCE).started()) {
      DatasetMatcher matcher = new DatasetMatcher(factory, ni, false);
      matcher.match(Datasets.COL, true);
    }
  }
  
  private static void updateSearchIndex(WsServerConfig cfg, SqlSessionFactory factory) throws Exception {
    if (cfg.es == null || cfg.es.isEmpty()) {
      LOG.warn("No ES configured, elastic search index not updated");
    
    } else {
      try (RestClient esClient = new EsClientFactory(cfg.es).createClient()) {
        LOG.info("Delete elastic search index {} on {}", cfg.es.nameUsage.name, cfg.es.hosts);
        EsUtil.deleteIndex(esClient, cfg.es.nameUsage);

        LOG.info("Build search index for draft catalogue");
        NameUsageIndexService indexService = new NameUsageIndexServiceEs(esClient, cfg.es, factory);
        indexService.indexDataset(Datasets.COL);
      }
    }
  }

  public static void setupColPartition(SqlSession session) {
    Partitioner.partition(session, Datasets.COL, DatasetOrigin.MANAGED);
    Partitioner.attach(session, Datasets.COL, DatasetOrigin.MANAGED);
    Partitioner.createManagedObjects(session, Datasets.COL);
    session.commit();
  }
  
  public static void exec(String name, ScriptRunner runner, Connection con, Reader reader) {
    try {
      LOG.info("Executing {}", name);
      runner.runScript(reader);
      con.commit();
    } catch (RuntimeException e) {
      LOG.error("Failed to execute {}", name);
      throw e;
      
    } catch (Exception e) {
      LOG.error("Failed to execute {}", name);
      throw new RuntimeException("Fail to execute sql file: " + name, e);
    }
  }
}
