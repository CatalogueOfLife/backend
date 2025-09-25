package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.matching.nidx.NameIndex;

import org.apache.commons.io.FileUtils;
import org.apache.fory.config.CompatibleMode;
import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;

import org.apache.fory.Fory;
import org.apache.fory.ThreadLocalFory;
import org.apache.fory.ThreadSafeFory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Factory to create and reuse persistent usage matchers,
 * which are specific for an entire dataset.
 */
public class UsageMatcherFactory implements DatasetListener {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherFactory.class);
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final File dir;
  private final MatchingConfig cfg;
  private final Int2ObjectMap<UsageMatcher> matchers = new Int2ObjectOpenHashMap<>();

  static final ThreadSafeFory FURY = new ThreadLocalFory(classLoader -> {
    Fory fury = Fory.builder()
      .withLanguage(org.apache.fory.config.Language.JAVA)
      .withClassLoader(classLoader)
      .withRefTracking(false)
      .withCompatibleMode(CompatibleMode.SCHEMA_CONSISTENT)
      .requireClassRegistration(true)
      .build();
    return register(fury);
  });
  private static Fory register(Fory fury) {
    fury.register(SimpleNameCached.class);
    fury.register(MatchType.class);
    fury.register(Rank.class);
    fury.register(NomCode.class);
    fury.register(TaxonomicStatus.class);
    return fury;
  }

  public UsageMatcherFactory(MatchingConfig cfg, NameIndex nameIndex, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
    this.cfg = cfg;
    this.dir = cfg.storageDir;
  }

  public NameIndex getNameIndex() {
    return nameIndex;
  }

  /**
   * Depending on the dataset origin either a persistent or postgres matcher is created.
   * Persistnent matchers are reused and should be closed after use.
   * @param datasetKey
   * @return
   */
  public UsageMatcher build(int datasetKey) {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.PROJECT) {
      var session = factory.openSession();
      return postgres(datasetKey, session, true);
    } else if (dir != null){
      return persistent(datasetKey);
    } else {
      return memory(datasetKey);
    }
  }

  /**
   * Creates a matcher that reads directly from Postgres and does not cache anything.
   * The given session is used and optionally closed when the matcher is closed.
   * @param datasetKey
   * @param session
   * @param closeSession
   */
  public UsageMatcher postgres(int datasetKey, SqlSession session, boolean closeSession) {
    LOG.info("Create new postgres matcher for dataset {}", datasetKey);
    var store = new UsageMatcherPgStore(datasetKey, session, closeSession);
    return new UsageMatcher(datasetKey, nameIndex, store);
  }

  public UsageMatcher persistent(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      LOG.info("Reuse existing persistent matcher for dataset {}", datasetKey);

    } else {
      LOG.info("Create new persistent matcher for dataset {}", datasetKey);
      var dbMaker = DBMaker
        .fileDB(dbFile(datasetKey))
        .fileMmapEnableIfSupported();
      var store = UsageMatcherMapDBStore.build(datasetKey, dbMaker);
      matchers.put(datasetKey, new UsageMatcher(datasetKey, nameIndex, store));
      // finally load the store - this can take a while so do it after putting the matcher into the map
      if (store.usages.isEmpty()) {
        store.load(factory);
      }
    }
    return matchers.get(datasetKey);
  }

  private File dbFile(int datasetKey) {
    return new File(dir, datasetKey + ".db");
  }

  public UsageMatcher memory(int datasetKey) {
    LOG.info("Create new in memory matcher for dataset {}", datasetKey);
    var store = new UsageMatcherMemStore(datasetKey);
    store.load(factory);
    return new UsageMatcher(datasetKey, nameIndex, store);
  }

  @Override
  public void datasetChanged(DatasetChanged d) {
    // we don't care
  }

  @Override
  public void datasetDataChanged(DatasetDataChanged event) {
    // remove any persistent matcher for the dataset that changed
    if (dir != null) {
      if (matchers.containsKey(event.datasetKey)) {
        LOG.info("Delete persistent matcher for dataset {} that has changed data", event.datasetKey);
        matchers.remove(event.datasetKey);
        var file = dbFile(event.datasetKey);
        FileUtils.deleteQuietly(file);
      }
    }
  }
}
