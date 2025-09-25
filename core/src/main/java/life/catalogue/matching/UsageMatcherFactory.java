package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.Managed;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.matching.nidx.NameIndex;

import org.apache.commons.io.FileUtils;
import org.apache.fory.config.CompatibleMode;
import org.apache.ibatis.session.SqlSession;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import org.apache.fory.Fory;
import org.apache.fory.ThreadLocalFory;
import org.apache.fory.ThreadSafeFory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * Factory to create and reuse persistent usage matchers,
 * which are specific for an entire dataset.
 */
public class UsageMatcherFactory implements DatasetListener, AutoCloseable {
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
   * Prepares a persistent matcher for the given datasetKey if it does not yet exist.
   *
   * Loading matchers can take a while so this method prepares them async
   * @param datasetKey
   * @return true if a matcher is being prepared, false if it already existed.
   */
  public boolean prepare(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      return false;
    }
    if (dir == null) {
      throw new IllegalStateException("Cannot prepare persistent matcher for dataset " + datasetKey + " because no storage dir is configured");
    }
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info.origin == DatasetOrigin.PROJECT) {
      throw new IllegalArgumentException("Cannot prepare persistent matcher for projects");
    }
    var m = persistent(datasetKey);
    if (!m.store().isEmpty()) {
      throw new IllegalArgumentException("Persistent matcher store for dataset " + datasetKey + " already contains data");
    }
    ForkJoinPool.commonPool().execute(new AsyncMatchLoader(m));
    return true;
  }

  private class AsyncMatchLoader implements Runnable {
    private final UsageMatcher matcher;

    public AsyncMatchLoader(UsageMatcher matcher) {
      this.matcher = matcher;
    }

    @Override
    public void run() {
      try {
        matcher.store().load(factory);
      } finally {
        matcher.close();
      }
    }

  }

  /**
   * If there is an existing persistent matcher for the given datasetKey it is returned,
   * otherwise a postgres matcher is created that reads directly from the db.
   *
   * All matchers must be closed after use to free up resources !
   * @param datasetKey
   * @return
   */
  public UsageMatcher existingOrPostgres(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      return matchers.get(datasetKey);
    }
    var f = dbFile(datasetKey);
    if (f.exists()) {
      return persistent(datasetKey);
    }
    return postgres(datasetKey, factory.openSession(), true);
  }

  /**
   * Creates a matcher that reads directly from Postgres and does not cache anything.
   * The given session is used and optionally closed when the matcher is closed.
   * @param datasetKey
   * @param session
   * @param closeSession if true the given session is closed when the matcher is closed
   */
  public UsageMatcher postgres(int datasetKey, SqlSession session, boolean closeSession) {
    LOG.info("Create new postgres matcher for dataset {}", datasetKey);
    var store = new UsageMatcherPgStore(datasetKey, session, closeSession);
    return new UsageMatcher(datasetKey, nameIndex, store, false);
  }

  public UsageMatcher persistent(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      LOG.info("Reuse existing persistent matcher for dataset {}", datasetKey);

    } else {
      LOG.info("Create new persistent matcher for dataset {}", datasetKey);
      var f = dbFile(datasetKey);
      var dbMaker = DBMaker
        .fileDB(f)
        .fileMmapEnableIfSupported();
      DB db;
      try {
        db = dbMaker.make();
      } catch (Exception e) {
        LOG.warn("Cannot open mapdb file {} for matcher of dataset {}. Remove and create an empty one: {}", f, datasetKey, e.getMessage());
        FileUtils.deleteQuietly(f);
        db = dbMaker.make();
      }
      var store = UsageMatcherMapDBStore.build(datasetKey, db);
      matchers.put(datasetKey, new UsageMatcher(datasetKey, nameIndex, store, true));
    }
    return matchers.get(datasetKey);
  }

  public UsageMatcher memory(int datasetKey) {
    LOG.info("Create new in memory matcher for dataset {}", datasetKey);
    var store = new UsageMatcherMemStore(datasetKey);
    return new UsageMatcher(datasetKey, nameIndex, store, false);
  }

  private File dbFile(int datasetKey) {
    return new File(dir, datasetKey + ".db");
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

  public static class FactoryMetadata {
    public final long instances;
    public final List<MatcherMetadata> matchers;

    public FactoryMetadata(List<MatcherMetadata> matchers) {
      this.matchers = matchers;
      this.instances = matchers.stream().filter(m -> m.online).count();
    }
  }
  public static class MatcherMetadata implements Comparable<MatcherMetadata> {
    @Override
    public int compareTo(@NotNull MatcherMetadata o) {
      return Integer.compare(datasetKey, o.datasetKey);
    }

    public final int datasetKey;
    public final boolean online;
    public final Integer size;

    public MatcherMetadata(int datasetKey, boolean online, Integer size) {
      this.datasetKey = datasetKey;
      this.online = online;
      this.size = size;
    }
  }

  public MatcherMetadata metadata(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      var m = matchers.get(datasetKey);
      return new MatcherMetadata(datasetKey, true, m.store().size());
    }
    var f = dbFile(datasetKey);
    if (f.exists()) {
      var m = persistent(datasetKey);
      return new MatcherMetadata(datasetKey, true, m.store().size());
    }
    return null;
  }

  public FactoryMetadata metadata() {
    List<MatcherMetadata> matchers = new ArrayList<>();
    IntSet keys = new IntOpenHashSet();
    for (var e : this.matchers.int2ObjectEntrySet()) {
      matchers.add(new MatcherMetadata(e.getIntKey(), true, e.getValue().store().size()));
      keys.add(e.getIntKey());
    }
    // look for more on disk
    if (dir != null) {
      FileUtils.listFiles(dir, new String[]{"db"}, false).forEach(f -> {
        try {
          int key = Integer.parseInt(f.getName().substring(0, f.getName().length() - 3));
          if (!keys.contains(key)) {
            matchers.add(new MatcherMetadata(key, false, null));
          }
        } catch (NumberFormatException e) {
          // ignore
        }
      });
    }
    Collections.sort(matchers);
    return new FactoryMetadata(matchers);
  }

  @Override
  public void close() throws Exception {
    for (UsageMatcher m : matchers.values()) {
      try {
        m.close();
      } catch (Exception e) {
        LOG.error("Failed to close matcher for dataset {}", m.datasetKey, e);
      }
    }
    matchers.clear();
  }
}
