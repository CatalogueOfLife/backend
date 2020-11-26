package life.catalogue.exporter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import life.catalogue.api.model.*;
import life.catalogue.common.concurrent.BackgroundJob;
import life.catalogue.common.concurrent.JobPriority;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.TaxonExtensionMapper;
import life.catalogue.db.mapper.VernacularNameMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

abstract class ArchiveExporter extends BackgroundJob {
  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExporter.class);

  private final SqlSessionFactory factory;
  private final int maxUsageIDs = 10000;

  private final int datasetKey;
  private final String startID;
  private final Rank lowestRank;
  private final boolean synonyms;
  private final Set<String> exclusions;

  protected final Set<String> taxonIDs = new HashSet<>();
  protected final LoadingCache<String, Reference> refCache;
  private SqlSession session;
  private File archive;

  ArchiveExporter(File archive, int datasetKey, @Nullable String startID, @Nullable Rank lowestRank, boolean synonyms, Set<String> exclusions,
                  int userKey, JobPriority priority, SqlSessionFactory factory) {
    super(priority, userKey);
    this.datasetKey = datasetKey;
    this.factory = factory;
    this.startID = startID;
    this.lowestRank = lowestRank;
    this.synonyms = synonyms;
    this.exclusions = exclusions;
    this.archive = archive;
    final DSID<String> rKey = DSID.of(datasetKey, null);
    this.refCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .build(new CacheLoader<>() {
        @Override
        public Reference load(String key) throws Exception {
          return session.getMapper(ReferenceMapper.class).get(rKey.id(key));
        }
      });
    LOG.info("Created exporter for dataset {}", datasetKey);
  }

  @Override
  public void execute() throws Exception {
    try (SqlSession session = factory.openSession(false)) {
      this.session = session;
      exportCore();
      exportExtensions();
      exportMetadata();
    }
    bundle();
  }

  private void exportCore() {
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      num.processTree(datasetKey, null, startID, exclusions, lowestRank, synonyms, true).forEach(u -> {
        if (u.isTaxon() && taxonIDs.size() < maxUsageIDs) {
          taxonIDs.add(u.getId());
        }
        write(u);
      });
    }
  }

  private void exportExtensions() {
    exportTaxonExtension(VernacularNameMapper.class, this::write);
  }

  private <T extends SectorScopedEntity<Integer>> void exportTaxonExtension(Class<? extends TaxonExtensionMapper<T>> mapperClass, BiConsumer<String, T> writer
  ) {
    try (SqlSession session = factory.openSession()) {
      TaxonExtensionMapper<T> exm = session.getMapper(mapperClass);
      if (taxonIDs.size() < maxUsageIDs) {
        var key = DSID.of(datasetKey, "");
        for (String id : taxonIDs) {
          for (T x : exm.listByTaxon(key.id(id))) {
            writer.accept(id, x);
          }
        }
      } else {
        exm.processDataset(datasetKey).forEach(x -> writer.accept(x.getTaxonID(), x.getObj()));
      }
    }
  }

  private void exportMetadata() {

  }

  private void bundle() {

  }


  abstract void write(NameUsageBase u);

  abstract void write(String id, VernacularName vn);

}
