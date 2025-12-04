package life.catalogue.matching;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.event.DatasetListener;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.config.MatchingConfig;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.nidx.NameIndex;

import org.apache.commons.io.filefilter.FileFileFilter;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.fory.Fory;
import org.apache.fory.ThreadLocalFory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.CompatibleMode;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Factory to create and reuse persistent usage matchers,
 * which are specific for an entire dataset.
 */
public class UsageMatcherFactory implements DatasetListener, AutoCloseable {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherFactory.class);
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  private final MatchingConfig cfg;
  private final File dir;
  private final ConcurrentHashMap<Integer, LocalDateTime> runningBuilds = new ConcurrentHashMap<>();
  private final Int2ObjectMap<UsageMatcher> matchers = new Int2ObjectOpenHashMap<>();
  private final JobExecutor executor;

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
    fury.register(TaxGroup.class);
    return fury;
  }

  public UsageMatcherFactory(MatchingConfig cfg, NameIndex nameIndex, SqlSessionFactory factory, JobExecutor executor) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.executor = executor;
    this.factory = Preconditions.checkNotNull(factory);
    this.dir = cfg.storageDir;
    this.cfg = cfg;
    // validate files on disk
    for (var key : listFS()) {
      try {
        DatasetInfoCache.CACHE.info(key);
      } catch (NotFoundException e) {
        File f = cfg.dir(key);
        LOG.warn("Dataset {} not existing, delete matching storage folder {}", key, f);
        FileUtils.deleteQuietly(f);
      }
    }
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
  public UsageMatcher build(int datasetKey) throws IOException {
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
  public BackgroundJob prepare(int datasetKey, int userKey) throws IOException {
    if (matchers.containsKey(datasetKey) && !matchers.get(datasetKey).store().isEmpty()) {
      return null;
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
    var job = new AsyncMatchLoader(m, userKey);
    executor.submit(job);
    return job;
  }

  private class AsyncMatchLoader extends BackgroundJob {
    private final UsageMatcher matcher;

    public AsyncMatchLoader(UsageMatcher matcher, int userKey) {
      super(userKey);
      this.matcher = matcher;
    }

    @Override
    public void execute() throws Exception {
      if (runningBuilds.contains(matcher.datasetKey)) {
        throw new IllegalStateException("Matcher for dataset " + matcher.datasetKey + " is already being built.");
      }
      try {
        runningBuilds.put(matcher.datasetKey, LocalDateTime.now());
        matcher.store().load(factory);
      } finally {
        matcher.close();
        var start = runningBuilds.remove(matcher.datasetKey);
        LOG.info("Matcher for dataset {} loaded in {} seconds", matcher.datasetKey, LocalDateTime.now().minusSeconds(start.getSecond()).getSecond());
      }
    }

    @Override
    public boolean isDuplicate(BackgroundJob other) {
      if (other instanceof AsyncMatchLoader) {
        return matcher.datasetKey == ((AsyncMatchLoader) other).matcher.datasetKey;
      }
      return super.isDuplicate(other);
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
  public UsageMatcher existingOrPostgres(int datasetKey) throws IOException {
    if (matchers.containsKey(datasetKey)) {
      return matchers.get(datasetKey);
    }
    var f = cfg.dir(datasetKey);
    if (f.isDirectory()) {
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

  public UsageMatcher persistent(int datasetKey) throws IOException {
    if (!matchers.containsKey(datasetKey)) {
      UsageMatcher m = buildNewMatcher(datasetKey);
      matchers.put(datasetKey, m);
    }
    return matchers.get(datasetKey);
  }

  private UsageMatcher buildNewMatcher(int datasetKey) throws IOException {
    if (runningBuilds.containsKey(datasetKey)) {
      throw new IllegalStateException("Matcher for dataset " + datasetKey + " is already being built. Please try again in a few minutes");
    }
    runningBuilds.put(datasetKey, LocalDateTime.now());
    try {
      var f = cfg.dir(datasetKey);

      UsageMatcherAbstractStore store;
      if (cfg.chronicle) {
        LOG.info("Create new persistent chronicle matcher for dataset {} at {}", datasetKey, f);
        long count;
        List<SimpleNameCached> samples;
        try (SqlSession s = factory.openSession()) {
          var um = s.getMapper(NameUsageMapper.class);
          count = 1000 + um.count(datasetKey);
          samples = um.listSN(datasetKey, new Page(0,5));
        }
        // this can take very long and will block !!!
        store = UsageMatcherChronicleStore.build(datasetKey, f, count, samples);

      } else {
        LOG.info("Create new persistent mapdb matcher for dataset {} at {}", datasetKey, f);
        DBMaker.Maker maker = DBMaker
          .fileDB(f)
          .fileMmapEnableIfSupported();
        store = UsageMatcherMapDBStore.build(datasetKey, maker.make());
      }
      return new UsageMatcher(datasetKey, nameIndex, store, true);

    } finally {
      var start = runningBuilds.remove(datasetKey);
      LOG.info("Matcher for dataset {} built in {} seconds", datasetKey, LocalDateTime.now().minusSeconds(start.getSecond()).getSecond());
    }
  }

  public UsageMatcher memory(int datasetKey) {
    LOG.info("Create new in memory matcher for dataset {}", datasetKey);
    var store = new UsageMatcherMemStore(datasetKey);
    return new UsageMatcher(datasetKey, nameIndex, store, false);
  }

  @Override
  public void datasetChanged(DatasetChanged d) {
    if (d.isDeletion()) {
      remove(d.key);
    }
  }

  @Override
  public void datasetDataChanged(DatasetDataChanged event) {
    // remove any persistent matcher for the dataset that changed
    remove(event.datasetKey);
  }

  public void remove(int datasetKey) {
    if (matchers.containsKey(datasetKey)) {
      LOG.info("Delete matcher for dataset {} due to changed data", datasetKey);
      var m = matchers.remove(datasetKey);
      if (m != null) {
        try {
          m.close();
        } catch (Exception e) {
          LOG.error("Failed to close matcher for dataset {}", datasetKey, e);
        }
      }
    }
    if (dir != null) {
      var file = cfg.dir(datasetKey);
      FileUtils.deleteQuietly(file);
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
    public DatasetSimple dataset;

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
    var f = cfg.dir(datasetKey);
    if (f.isDirectory()) {
      try {
        var m = persistent(datasetKey);
        return new MatcherMetadata(datasetKey, true, m.store().size());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  public FactoryMetadata metadata(boolean decorate) {
    List<MatcherMetadata> matchers = new ArrayList<>();
    IntSet keys = new IntOpenHashSet();
    for (var e : this.matchers.int2ObjectEntrySet()) {
      matchers.add(new MatcherMetadata(e.getIntKey(), true, e.getValue().store().size()));
      keys.add(e.getIntKey());
    }
    // look for more on disk
    for (var key : listFS()) {
      if (!keys.contains(key)) {
        matchers.add(new MatcherMetadata(key, false, null));
        keys.add(key);
      }
    }
    // decorate with dataset metadata
    if (decorate) {
      try (SqlSession session = factory.openSession()) {
        var dm = session.getMapper(DatasetMapper.class);
        for (MatcherMetadata m : matchers) {
          m.dataset = dm.getSimple(m.datasetKey);
        }
      }
    }
    // sort by datasetKey
    Collections.sort(matchers);
    return new FactoryMetadata(matchers);
  }

  private List<Integer> listFS() {
    List<Integer> keys = new ArrayList<>();
    if (dir != null && dir.isDirectory()) {
      FilenameFilter ff = cfg.chronicle ? DirectoryFileFilter.INSTANCE : FileFileFilter.INSTANCE;
      String[] files = dir.list(ff);
      for (var fn : files) {
        try {
          int key = Integer.parseInt(fn);
          keys.add(key);
        } catch (NumberFormatException e) {
          // ignore
        }
      }
    }
    return keys;
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
