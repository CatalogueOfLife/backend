package life.catalogue.matching;

import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.core.setup.Bootstrap;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.MatchingServerConfig;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.db.MybatisFactory;

import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;

import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import life.catalogue.db.mapper.NamesIndexMapper;

import life.catalogue.matching.nidx.NameIndexImpl;

import net.sourceforge.argparse4j.inf.Namespace;

import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.core.cli.ConfiguredCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ibm.icu.text.MessagePatternUtil.MessageContentsNode.Type.ARG;

public class MatchingCmd extends ConfiguredCommand<MatchingServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingCmd.class);
  private static final String ARG_DATASET_KEY = "datasetKey";

  public MatchingCmd() {
    super("build-match-storage", "Builds a new matching storage on disk for the given dataset key");
  }
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--" + ARG_DATASET_KEY)
      .dest(ARG_DATASET_KEY)
      .type(Integer.class)
      .required(true)
      .help("Target dataset key to build the match storage for");
  }

  @Override
  protected void run(Bootstrap<MatchingServerConfig> bootstrap, Namespace namespace, MatchingServerConfig cfg) throws Exception {
    LOG.info("Connecting to database {} on {}", cfg.db.database, cfg.db.host);
    HikariDataSource dataSource = null;
    try {
      dataSource = cfg.db.pool();
      SqlSessionFactory factory = MybatisFactory.configure(dataSource, getClass().getSimpleName());

      MatchingStorageMetadata metadata;
      final int datasetKey = namespace.getInt(ARG_DATASET_KEY);
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        var nmm = session.getMapper(NameMatchMapper.class);
        var num = session.getMapper(NameUsageMapper.class);

        var d = dm.get(datasetKey);
        LOG.info("Build new matching index for dataset {}: {} - {}: {}", datasetKey, d.getAlias(), d.getVersion(), d.getTitle());
        metadata = new MatchingStorageMetadata(d);
        metadata.setNumUsages( num.count(datasetKey) );
        metadata.setNumCanonicals( nmm.countCanonIds(datasetKey) );
        metadata.setNumNidx( nmm.countIndexIds(datasetKey) );
      }

      final MatchingStorageChrononicle storage = MatchingStorageChrononicle.create(cfg.matching.storage, cfg.matching.poolSize, metadata);
      LOG.info("Copy data");
      try (SqlSession session = factory.openSession()) {
        var num = session.getMapper(NameUsageMapper.class);
        var nim = session.getMapper(NamesIndexMapper.class);

        int lastCanonId = -99999;
        final NameIndexImpl nidx = (NameIndexImpl) storage.getNameIndex();
        IntSet nidxIds = new IntOpenHashSet();
        IntSet canonIds = new IntOpenHashSet();
        List<SimpleNameCached> canonGroup = new ArrayList<>();
        try (var cursor = num.processDatasetSimpleNidx(datasetKey)){
          for (var sn : cursor) {
            // name index
            if (sn.getCanonicalId() != null && !canonIds.contains((int)sn.getCanonicalId())) {
              canonIds.add((int)sn.getCanonicalId());
              var idxName = nim.get(sn.getCanonicalId());
              nidx.addToStore(idxName);
            }
            if (sn.getNamesIndexId() != null && !nidxIds.contains((int)sn.getNamesIndexId())) {
              nidxIds.add((int)sn.getNamesIndexId());
              var idxName = nim.get(sn.getNamesIndexId());
              nidx.addToStore(idxName);
            }
            // usage
            storage.put(sn);
            // by canonical
            if (lastCanonId != sn.getCanonicalId()) {
              if (!canonGroup.isEmpty()) {
                storage.put(lastCanonId, canonGroup);
                canonGroup = new ArrayList<>();
              }
              lastCanonId = sn.getCanonicalId();
            }
            canonGroup.add(sn);
          }
          // persist last group
          if (!canonGroup.isEmpty()) {
            storage.put(lastCanonId, canonGroup);
          }
        }
      }
      storage.close();

    } finally {
      LOG.info("Shutdown matching command");
      if (dataSource != null) {
        dataSource.close();
      }
    }
  }
}
