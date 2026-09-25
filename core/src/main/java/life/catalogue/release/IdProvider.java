package life.catalogue.release;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.collection.CountMap;
import life.catalogue.common.collection.Int2IntBiMap;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.matching.NameIdentity;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.matching.UsageMatcherFileStoreBuilder;
import life.catalogue.matching.UsageMatcherStore;
import life.catalogue.release.ReleasedIds.ReleasedId;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import it.unimi.dsi.fastutil.ints.*;

/**
 * Generates a usage id mapping table that maps all name usages from the project source
 * to some stable integer based identifiers.
 * The newly generated mapping table can be used in copy dataset commands
 * during the project release.
 *
 * Prerequisites:
 *     - names match up to date
 *
 * Basic steps:
 *
 * 1) Build a {@link ReleasedIds} view of every id this project ever released, from {@code name_usage_archive}.
 *    Ids are kept as ints to save memory, bucketed by their canonical names index id, and carry the few properties
 *    the comparison needs plus what makes them senior: the earliest release they appeared in, how many releases
 *    carried them, whether the last release still had them and whether a base release ever used them.
 *    Deleted ids are included - they are the resurrection candidates.
 *
 * 2) Walk the usages to be released one canonical names index group at a time, i.e. all usages sharing the same name
 *    regardless of authorship, which after the move to a canonical only names index is the entire name based grouping.
 *
 * 3) Within a group, compare every usage against every released id of that group with {@link NameIdentity}, which
 *    answers three valued per attribute: a contradiction (a genuinely different authorship, an incompatible rank, a
 *    misapplied name against a non misapplied one, and unless the authorship agrees a disparate tax group or two
 *    different nomenclatural codes) rules a pairing out altogether, while missing information - an authorship that
 *    was added or removed, an unranked name - never does. See <a href="https://github.com/CatalogueOfLife/backend/issues/1326">#1326</a>.
 *    Resurrecting an id the last release no longer had needs more than the absence of contradictions.
 *
 * 4) Hand the ids out greedily, best pairing first, see {@link IdCandidate} for the ordering. Usages left without an
 *    id get a freshly minted one; released ids left over that the last release still had count as deleted.
 */
public class IdProvider {
  protected final Logger LOG = LoggerFactory.getLogger(IdProvider.class);
  // UNITE species-hypothesis codes, e.g. SH19186714.17FU - used verbatim as the usage id
  protected static final Pattern UNITE_ID = Pattern.compile("^SH(\\d+)\\.(\\d+)FU$", Pattern.CASE_INSENSITIVE);
  // BOLD codes, e.g. BOLD:AAA3374 - the colon is replaced by a dot to form the usage id
  protected static final Pattern BOLD_ID = Pattern.compile("^BOLD:[A-Z0-9]+$", Pattern.CASE_INSENSITIVE);
  static final Function<SimpleNameWithNidx, String> NO_ACCEPTED_NAMES = n -> null;
  private final int projectKey;
  private final int attempt;
  private final DatasetOrigin origin;
  private final int mappedDatasetKey; // from
  private final int releaseDatasetKey; // to
  private final @Nullable Integer lastReleaseKey;
  private @Nullable Integer prevReleaseKey;
  // extended releases only: the base release before the one this release extends, see #setPrevBaseReleaseKey
  private @Nullable Integer prevBaseReleaseKey;
  private final SqlSessionFactory factory;
  private final TaxGroupAnalyzer groupAnalyzer;
  private final NameIdentity identity = new NameIdentity();
  private final ReleaseConfig cfg;
  private final ProjectReleaseConfig prCfg;
  private final ReleasedIds ids;
  private final Int2IntBiMap dataset2attempt = new Int2IntBiMap();
  private final Int2ObjectMap<Release> dataset2release = new Int2ObjectOpenHashMap<>();
  private final IntSet unknownReleases = new IntOpenHashSet(); // release keys the archive names but the db no longer has
  private final AtomicInteger keySequence = new AtomicInteger();
  private final File reportDir;
  // id changes in this release
  private int reused = 0;
  private final IntSet created = new IntOpenHashSet();
  private Int2IntMap deleted = new Int2IntOpenHashMap(); // maps to release attempt for reporting!
  private final Int2IntMap resurrected = new Int2IntOpenHashMap(); // maps to release attempt for reporting!
  // a dying id -> the id of the usage that took it over, see #recordSuperseded
  private final Int2IntMap superseded = new Int2IntOpenHashMap();
  private final SortedMap<String, List<InstableName>> unstable = new TreeMap<>();
  private final CountMap<String> uniteVersions = new CountMap<>();
  protected IdMapMapper idm;
  protected NameUsageMapper num;
  protected NameMatchMapper nmm;

  static class Release {
    final int key;
    final int attempt;
    final DatasetOrigin origin;

    Release(int key, DatasetOrigin origin, int attempt) {
      this.key = key;
      this.origin = origin;
      this.attempt = attempt;
    }
  }
  public IdProvider(int projectKey, int mappedDatasetKey, DatasetOrigin origin, int attempt, int releaseDatasetKey,
                    ReleaseConfig cfg, ProjectReleaseConfig prCfg, SqlSessionFactory factory
  ) {
    LOG.info("Setup ID provider for project {}, mapping dataset {}", projectKey, mappedDatasetKey);
    groupAnalyzer = new TaxGroupAnalyzer();
    this.releaseDatasetKey = releaseDatasetKey;
    this.mappedDatasetKey = mappedDatasetKey;
    this.projectKey = projectKey;
    this.origin = origin;
    this.attempt = attempt;
    this.factory = factory;
    this.cfg = cfg;
    this.prCfg = prCfg;
    if (prCfg.ignoredReleases == null) prCfg.ignoredReleases = new ArrayList<>(); // avoid NPEs down the line, simpler
    reportDir = cfg.reportDir(projectKey, attempt);
    reportDir.mkdirs();
    dataset2attempt.put(releaseDatasetKey, attempt);
    // load a map of all releases to their attempts and figure out last release
    lastReleaseKey = loadReleaseAttempts();
    // now build the main release identifier store
    ids = new ReleasedIds();
    // create report dirs
    File dir = cfg.reportDir(projectKey, attempt);
    dir.mkdirs();

    if (cfg.restart != null) {
      LOG.info("Use ID provider with no previous IDs. Start ID sequence with {} ({})", cfg.restart, encode(cfg.restart));
      keySequence.set(cfg.restart);

    } else {
      // populate ids from db
      loadPreviousReleaseIds();
      LOG.info("Last release {} with {} IDs", lastReleaseKey, ids.currentIdCount());
      keySequence.set(ids.maxKey());
      LOG.info("Max existing id = {} ({}). Start ID sequence with {} ({})", ids.maxKey(), encode(ids.maxKey()), peek(), encode(peek()));
    }
  }

  /**
   * @param prevReleaseKey the previous release of the same origin, used to keep name ids sticky. Optional.
   */
  public void setPrevReleaseKey(@Nullable Integer prevReleaseKey) {
    this.prevReleaseKey = prevReleaseKey;
  }

  /**
   * Extended releases carry every id of their base release, so an id the new base release dropped is also gone from
   * the extended release, although the extended release had no say in it. Knowing the base release before the one
   * being extended lets the reports tell those deletions apart from the extended release's own.
   *
   * @param prevBaseReleaseKey the base release preceding the one this extended release is built on. Optional.
   */
  public void setPrevBaseReleaseKey(@Nullable Integer prevBaseReleaseKey) {
    this.prevBaseReleaseKey = prevBaseReleaseKey;
  }

  /**
   * @return preview the key that will be issued next without changing the sequence
   */
  public int peek() {
    return keySequence.get()+1;
  }

  public static class InstableName implements DSID<String> {
    public final boolean del;
    public final int datasetKey;
    public final String id;
    public final String fullname;
    public final Rank rank;
    public final TaxonomicStatus status;
    public final String parent;

    public InstableName(boolean del, DSID<String> key, SimpleName sn) {
      this.del = del;
      this.datasetKey = key.getDatasetKey();
      this.id = key.getId();
      this.fullname = sn.getLabel();
      this.rank = sn.getRank();
      this.status = sn.getStatus();
      this.parent = sn.getParent();
    }

    public boolean isDel() {
      return del;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      throw new UnsupportedOperationException(getClass().getSimpleName() + " is final");
    }

    @Override
    public Integer getDatasetKey() {
      return datasetKey;
    }

    @Override
    public void setDatasetKey(Integer key) {
      throw new UnsupportedOperationException(getClass().getSimpleName() + " is final");
    }
  }

  public static class IdReport {
    public final IntSet created;
    public final Int2IntMap deleted;
    public final Int2IntMap resurrected;
    /** a deleted id -> the id that took it over, a subset of deleted */
    public final Int2IntMap superseded;

    IdReport(IntSet created, Int2IntMap deleted, Int2IntMap resurrected, Int2IntMap superseded) {
      this.created = created;
      this.deleted = deleted;
      this.resurrected = resurrected;
      this.superseded = superseded;
    }
  }

  public IdReport getReport() {
    return new IdReport(created, deleted, resurrected, superseded);
  }

  /**
   * Stages the supersede pairs against the release being built - an abandoned release must not leave a redirect on a
   * still live id. Archiving the published release drops them, and folds them into the project archive first only if
   * that release decides redirects: the highest ranked supplying base or extended release of the newest generation,
   * see ReleaseRanking.decidesRedirects.
   */
  private void persistSuperseded() {
    if (superseded.isEmpty()) {
      return;
    }
    try (SqlSession session = factory.openSession(false)) {
      var anum = session.getMapper(ArchivedNameUsageMapper.class);
      anum.deleteSuperseded(releaseDatasetKey); // a previous, failed attempt at this very release
      for (var entry : superseded.int2IntEntrySet()) {
        anum.addSuperseded(releaseDatasetKey, encode(entry.getIntKey()), encode(entry.getIntValue()));
      }
      session.commit();
      LOG.info("Staged {} superseded ids for release {}", superseded.size(), releaseDatasetKey);
    } catch (RuntimeException e) {
      LOG.error("Failed to stage {} superseded ids for release {}", superseded.size(), releaseDatasetKey, e);
    }
  }

  protected void report() {
    try (var tmp = TempFile.directory()){
      // a deleted id is by definition in the last release, which also still exists - unlike the release it first
      // appeared in, which is often deleted by now and would leave the report without a name
      final IntSet baseDeleted = baseDeletions();
      final IntSet ownDeleted = new IntOpenHashSet(deleted.keySet());
      ownDeleted.removeAll(baseDeleted);
      reportFile(tmp.file,"deleted.tsv", ownDeleted, id -> deletedIn(id), true);
      if (prevBaseReleaseKey != null) {
        reportFile(tmp.file,"base-deleted.tsv", baseDeleted, id -> prevBaseReleaseKey, true);
      }
      // resurrected and created ids are shown with the usage of this release that carries them
      reportFile(tmp.file,"resurrected.tsv", resurrected.keySet(), id -> releaseDatasetKey, false);
      reportFile(tmp.file,"created.tsv", created, id -> releaseDatasetKey, false);
      reportSuperseded(tmp.file);
      // clear instable names, removing the ones with just deletions
      unstable.entrySet().removeIf(entry -> entry.getValue().parallelStream().allMatch(n -> n.del));
      final var unstableFile = new File(tmp.file, "unstable.txt");
      LOG.info("Writing unstable ID report for project release {}-{} to {}", projectKey, attempt, unstableFile);
      try (Writer writer = UTF8IoUtils.writerFromFile(unstableFile);
          SqlSession session = factory.openSession(true)
      ) {
        nmm = session.getMapper(NameMatchMapper.class);
        for (var entry : unstable.entrySet()) {
          writer.write(entry.getKey() + "\n");
          entry.getValue().sort(Comparator.comparing(InstableName::isDel).reversed());
          entry.getValue().forEach(n -> writeInstableName(writer, n));
        }
      }
      // unite versions
      if (!uniteVersions.isEmpty()) {
        var sb = new StringBuilder();
        for (var entry : uniteVersions.entrySet()) {
          sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("; ");
        }
        if (uniteVersions.size() > 1) {
          LOG.warn("Found {} UNITE versions: {}", uniteVersions.size(), sb);
        } else {
          LOG.info("Found UNITE version: {}", sb);
        }
      }
      final var idZip = new File(cfg.reportDir(projectKey, attempt), "id-reports.gz");
      LOG.info("Zipping up id reports for project release {}-{} to {}", projectKey, attempt, idZip);
      CompressionUtil.zipDir(tmp.file, idZip);
    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project "+projectKey, e);
    }
    LOG.info("ID provision done. Reused {} stable IDs for project release {}-{} ({}), resurrected={}, newly created={}, deleted={}", reused, projectKey, attempt, releaseDatasetKey, resurrected.size(), created.size(), deleted.size());
  }

  /**
   * Which of the deleted ids were taken over by another id rather than simply vanishing, as
   * {@code oldId, newId, rank, status, name, authorship} of the surviving usage.
   */
  private void reportSuperseded(File dir) throws IOException {
    if (superseded.isEmpty()) {
      return;
    }
    File f = new File(dir, "superseded.tsv");
    try (TabWriter tsv = TabWriter.fromFile(f);
         SqlSession session = factory.openSession(true)
    ) {
      var num = session.getMapper(NameUsageMapper.class);
      LOG.info("Writing superseded ID report for project release {}-{} of {} IDs to {}", projectKey, attempt, superseded.size(), f);
      for (int id : superseded.keySet().intStream().sorted().toArray()) {
        final String newID = encode(superseded.get(id));
        var sn = num.getSimple(DSID.of(releaseDatasetKey, newID));
        tsv.write(new String[]{
          encode(id),
          newID,
          sn == null ? null : VocabularyUtils.toString(sn.getRank()),
          sn == null ? null : VocabularyUtils.toString(sn.getStatus()),
          sn == null ? null : sn.getName(),
          sn == null ? null : sn.getAuthorship()
        });
      }
    }
  }

  private void writeInstableName(Writer writer, InstableName n) {
    try {
      writer.write(' ');
      writer.write(n.del ? '-' : '+');
      writer.write(' ');
      writer.write(n.fullname);
      writer.write(" [");
      writer.write(String.valueOf(n.status));
      writer.write(' ');
      writer.write(String.valueOf(n.rank));
      writer.write(' ');
      writer.write(String.valueOf(n.datasetKey));
      writer.write(':');
      writer.write(n.id);

      NameMatch match = nmm.get(n);
      writer.write(" nidx=");
      if (match != null) {
        // single-tier index: the names index id is its own canonical, so both are the same value
        writer.write(String.valueOf(match.getNidx()));
        writer.write('/');
        writer.write(String.valueOf(match.getNidx()));
      } else {
        writer.write("null");
      }

      if (n.parent != null && n.status.isSynonym()) {
        writer.write(" parent=");
        writer.write(n.parent);
      }
      writer.write(']');
      writer.write('\n');
    } catch (IOException e) {
      LOG.error("Failed to report unstable name {}", n.fullname, e);
    }
  }

  /**
   * @return the ids of the last release this release no longer has because the new base release dropped them.
   *   Always empty unless this is an extended release that knows its previous base release.
   */
  private IntSet baseDeletions() {
    final IntSet ids = new IntOpenHashSet();
    if (prevBaseReleaseKey != null && !deleted.isEmpty()) {
      try (SqlSession session = factory.openSession(true)) {
        var mapper = session.getMapper(NameUsageMapper.class);
        final DSIDValue<String> key = DSID.root(prevBaseReleaseKey);
        for (int id : deleted.keySet()) {
          if (mapper.exists(key.id(encode(id)))) {
            ids.add(id);
          }
        }
      }
      LOG.info("{} of {} deleted ids were dropped by the base release already, as release {} had them", ids.size(), deleted.size(), prevBaseReleaseKey);
    }
    return ids;
  }

  /**
   * @return the dataset to show a deleted id from: the last release, or if there is none the release it first appeared in
   */
  private int deletedIn(int id) {
    if (lastReleaseKey != null) {
      return lastReleaseKey;
    }
    int attempt = deleted.get(id);
    return dataset2attempt.containsValue(attempt) ? dataset2attempt.getKey(attempt) : releaseDatasetKey;
  }

  private void reportFile(File dir, String filename, IntSet ids, Int2IntFunction datasetLookup, boolean deletion) throws IOException {
    File f = new File(dir, filename);
    try(TabWriter tsv = TabWriter.fromFile(f);
        SqlSession session = factory.openSession(true)
    ) {
      num = session.getMapper(NameUsageMapper.class);
      LOG.info("Writing ID report for project release {}-{} of {} IDs to {}", projectKey, attempt, ids.size(), f);
      ids.intStream()
        .sorted()
        .forEach(id -> reportId(id, datasetLookup.get(id), tsv, deletion));
    }
  }

  /**
   * @param datasetKey the release to show the id's usage from
   * @param deletion true for an id this release drops
   */
  private void reportId(int id, int datasetKey, TabWriter tsv, boolean deletion){
    String ID = IdConverter.LATIN29.encode(id);
    SimpleName sn = null;
    DSID<String> key = null;
    try {
      key = DSID.of(datasetKey, ID);
      sn = num.getSimple(key);

      if (sn == null) {
        LOG.warn("ID {} [{}] reported without name usage in dataset {}", ID, id, datasetKey);
        tsv.write(new String[]{
          ID,
          null,
          null,
          null,
          null
        });

      } else {
        // always use the new stable identifier, not the projects temporary one
        sn.setId(ID);
        tsv.write(new String[]{
          ID,
          VocabularyUtils.toString(sn.getRank()),
          VocabularyUtils.toString(sn.getStatus()),
          sn.getName(),
          sn.getAuthorship()
        });
        // populate unstable names report
        // expects deleted names to come first, so we can avoid adding many created ids for those which have not also been deleted
        if (deletion) {
          unstable.putIfAbsent(sn.getName(), new ArrayList<>());
        }
        if (unstable.containsKey(sn.getName())) {
          unstable.get(sn.getName()).add(new InstableName(deletion, key, sn));
        }
      }

    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to report ID {}: {} [key={}, sn={}]", id, ID, key, sn, e);
    }
  }

  @VisibleForTesting
  protected void addRelease(Release release) {
    dataset2release.put(release.key, release);
    dataset2attempt.put(release.key, release.attempt);
  }

  @VisibleForTesting
  protected Integer loadReleaseAttempts() {
    Integer lrkey = null; // latest release key
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      lrkey = dm.latestRelease(projectKey, true, prCfg.ignoredReleases, origin);
      // the archive holds the ids of every public release the project ever had, deleted ones included - a deleted
      // release keeps its dataset row and with it its attempt, and without it an archived id of such a release would
      // rank last on seniority. Private releases never reach the archive and have no business here at all
      dm.listReleasesQuick(projectKey, true, false).forEach(d -> {
        dataset2release.put(d.getKey(), new Release(d.getKey(), d.getOrigin(), d.getAttempt()));
        if (d.getKey() != releaseDatasetKey) {
          if (prCfg.ignoredReleases.contains(d.getKey())) {
            LOG.info("Configured to ignore release {}", d.getKey());
          } else {
            dataset2attempt.put(d.getKey(), d.getAttempt());
          }
        }
      });
      LOG.info("Found {} relevant past releases, deleted ones included", dataset2attempt.size());
    }
    return lrkey;
  }

  static class LoadStats {
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger nomatches = new AtomicInteger();
    AtomicInteger temporary = new AtomicInteger();
    AtomicInteger ignored = new AtomicInteger();

    @Override
    public String toString() {
      return String.format("%s usages ignoring %s with %s temporary ids and %s missing matches", counter, ignored, nomatches, temporary);
    }
  }

  /**
   * Loads all archived usages with all ever issued identifiers for this project, preferring the earliest version of any id.
   */
  @VisibleForTesting
  protected void loadPreviousReleaseIds(){
    // read the entire names archive
    try (SqlSession session = factory.openSession(true)) {
      final int sizeBefore = ids.size();
      final LoadStats stats = new LoadStats();
      LOG.info("Read all archived names");
      PgUtils.consume(
        () -> session.getMapper(ArchivedNameUsageMapper.class).processArchivedUsages(projectKey),
        sn -> addReleaseId(sn, stats)
      );
      LOG.info("Read {} from archived names. Adding {} previously used ids to a total of {}", stats, ids.size() - sizeBefore, ids.size());
      //ids.log();
    }
  }

  /**
   * @param sn simple name with parent being a scientificName, not ID!
   */
  @VisibleForTesting
  protected void addReleaseId(ArchivedNameUsageMapper.ArchivedSimpleName sn, LoadStats stats){
    stats.counter.incrementAndGet();
    // use the first not ignored release
    int firstReleaseKey = -1;
    int releaseCount = 0;
    boolean isCurrent = false;
    // make sure keys are sorted chronologically, starting with earliest
    var rkeys = sn.getReleaseKeys();
    Arrays.sort(rkeys);
    for (int key : rkeys) {
      if (prCfg.ignoredReleases.contains(key)) {
        continue;
      }
      // how many releases carried this id is what makes it senior, so we cannot stop early anymore
      releaseCount++;
      if (firstReleaseKey < 0) {
        firstReleaseKey = key;
      }
      if (lastReleaseKey != null && key == lastReleaseKey) {
        isCurrent = true;
      }
    }
    if (firstReleaseKey == -1) {
      stats.ignored.incrementAndGet();
      LOG.info("Ignoring ID {} from all releases: {}", sn.getId(), sn.getLabel());
      // ignored releases were still published, so their ids must never be issued again
      try {
        ids.considerMaxID(IdConverter.LATIN29.decode(sn.getId()));
      } catch (IllegalArgumentException e) {
        // expected for temp identifiers, which the sequence never issues
      }

    } else {
      try {
        if (sn.getNamesIndexId() == null) {
          var intID = IdConverter.LATIN29.decode(sn.getId());
          ids.considerMaxID(intID);
          stats.nomatches.incrementAndGet();
          LOG.warn("Existing release id {}:{} without a names index id. Skip {}", firstReleaseKey, sn.getId(), sn.getLabel());

        } else {
          sn.setGroup( groupAnalyzer.analyze(sn, sn.getClassification()) );
          var rl = ReleasedId.create(sn, attemptOf(firstReleaseKey), releaseCount, isCurrent, isXrOnly(rkeys));
          ids.add(rl);
          LOG.debug("Add {} from {}/{}: {}", sn.getId(), rl.attempt, firstReleaseKey, sn);
        }
      } catch (IllegalArgumentException e) {
        // expected for temp identifiers, swallow and count
        stats.temporary.incrementAndGet();
      }
    }
  }

  /**
   * The attempt of the release an archived id first appeared in, which is how senior that id is.
   *
   * Deleted releases are loaded like any other, so only a release whose dataset row is gone for good is
   * unknown here. Such an id must not pass for the oldest one: an unknown key resolves to attempt 0 through the
   * primitive map, which is older than every real attempt and made ids of vanished releases outrank ids in
   * continuous use. They rank last on seniority instead and can still win on evidence alone.
   */
  @VisibleForTesting
  int attemptOf(int releaseKey) {
    if (dataset2attempt.containsKey(releaseKey)) {
      return dataset2attempt.getValue(releaseKey);
    }
    if (unknownReleases.add(releaseKey)) {
      LOG.warn("Archived ids reference release {} which no longer exists. They rank last on seniority", releaseKey);
    }
    return Integer.MAX_VALUE;
  }

  /**
   * @return true if all not ignored releases of an id are extended releases.
   *   A release we do not know at all - one whose dataset row is gone - never counts as an extended release.
   */
  private boolean isXrOnly(int[] releaseKeys) {
    boolean xr = false;
    for (int key : releaseKeys) {
      if (!prCfg.ignoredReleases.contains(key)) {
        var rel = dataset2release.get(key);
        if (rel == null || rel.origin != DatasetOrigin.XRELEASE) {
          return false;
        }
        xr = true;
      }
    }
    return xr;
  }

  @VisibleForTesting
  protected Int2IntBiMap getDatasetAttemptMap(){
    return dataset2attempt;
  }

  protected Writer buildNomatchWriter() throws IOException {
    return UTF8IoUtils.writerFromFile(new File(reportDir, "nomatch.txt"));
  }

  protected void mapAllIds(){
    mapIds(false);
  }
  protected void mapTempIds(){
    mapIds(true);
  }

  private void mapIds(boolean tempOnly){
    int count;
    try (SqlSession session = factory.openSession(true)) {
      count = session.getMapper(NameUsageMapper.class).count(mappedDatasetKey);
    }
    try (var tf = TempFile.directory()) {
      int cntLoaded;
      try (var builder = new UsageMatcherFileStoreBuilder(mappedDatasetKey, tf.file)) {
        cntLoaded = builder.load(factory);
        try (var store = builder.seal()) {
          int cntStore = store.size();
          if (cntStore != cntLoaded || cntStore != count) {
            LOG.warn("Mismatch between counted, loaded and stored usage counts: {}/{}/{}", count, cntLoaded, cntStore);
          }
          store.analyze(groupAnalyzer);
          mapIds(store, tempOnly);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return true if the id is a stable release identifier as issued by #encode(int).
   *   Anything else, e.g. a ShortUUID or UUID, is a temporary id.
   */
  public static boolean isStableId(String id) {
    // IdConverter.LATIN29 encodes any int with at most 7 characters (29^7 > 2^31)
    if (id == null || id.length() > 7) {
      return false;
    }
    try {
      IdConverter.LATIN29.decode(id);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * @param tempOnly if true only usages with a temporary id are mapped, keeping existing stable ids as they are
   */
  @VisibleForTesting
  protected void mapIds(UsageMatcherStore uStore, boolean tempOnly){
    LOG.info("Map {} name usage IDs from dataset {}{}", uStore.size(), mappedDatasetKey, tempOnly ? ", temporary ids only" : "");
    final int lastRelIds = ids.currentIdCount();
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession writeSession = factory.openSession(false);
         Writer nomatchWriter = buildNomatchWriter()
    ) {
      idm = writeSession.getMapper(IdMapMapper.class);
      final int batchSize = 10000;

      for (var canonId : uStore.allCanonicalIds()) {
        var all = uStore.simpleNamesByCanonicalId(canonId);
        var names = all;
        List<SimpleNameWithNidx> kept = List.of();
        if (tempOnly && !all.isEmpty()) {
          names = new ArrayList<>();
          kept = new ArrayList<>();
          for (var n : all) {
            if (isStableId(n.getId())) {
              kept.add(n);
            } else {
              names.add(n);
            }
          }
        }
        issueIDs(canonId, names, kept, acceptedNames(all, uStore), nomatchWriter);
        int before = counter.get() / batchSize;
        int after = counter.addAndGet(names.size()) / batchSize;
        if (before != after) {
          writeSession.commit();
        }
      }
      writeSession.commit();

    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project " + projectKey, e);
    }
    mapNameIds();
    reportTemporaryIds(tempOnly);
    // ids remaining from the current attempt will be deleted
    deleted = ids.currentIDs();
    reused = lastRelIds - deleted.size();
    persistSuperseded();
    LOG.info("Done mapping name usage IDs. {} ids from the last release will be deleted ({} of them superseded by another id), {} have been reused.",
      deleted.size(), superseded.size(), reused);
  }

  /**
   * Gives every name the stable id of one of its own usages, so a name is as stable as the usages that carry it and
   * an exported NameID means the same thing from one release to the next. See IdMapMapper#mapNamesFromUsages.
   *
   * Nothing is matched a second time here - names have no identity of their own in this scheme, which is the point:
   * it cannot drift away from the usage ids and it costs one statement.
   */
  private void mapNameIds() {
    if (!prCfg.stableNameIds) {
      return;
    }
    try (SqlSession session = factory.openSession(true)) {
      int mapped = session.getMapper(IdMapMapper.class).mapNamesFromUsages(mappedDatasetKey, prevReleaseKey);
      LOG.info("Mapped {} name ids of dataset {} from their usages", mapped, mappedDatasetKey);
    }
  }

  /**
   * Writes all usages which did not get an id mapping to temporary.tsv, as they keep their original id in the release.
   * These are usages without a names index match, which cannot be given a stable id.
   *
   * @param tempOnly if true usages with a stable id are not reported as they were never meant to be mapped
   */
  private void reportTemporaryIds(boolean tempOnly) {
    final File file = new File(reportDir, "temporary.tsv");
    final List<String> examples = new ArrayList<>();
    int counter = 0;
    try (SqlSession session = factory.openSession(true);
         TabWriter writer = TabWriter.fromFile(file)
    ) {
      for (SimpleName sn : session.getMapper(IdMapMapper.class).processUnmappedUsages(mappedDatasetKey)) {
        if (tempOnly && isStableId(sn.getId())) {
          continue;
        }
        writer.write(new String[]{
          sn.getId(), Objects.toString(sn.getStatus(), null), Objects.toString(sn.getRank(), null), sn.getName(), sn.getAuthorship()
        });
        if (counter++ < 3) {
          examples.add(sn.getId() + " " + sn.getLabel());
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to write temporary id report for project " + projectKey, e);
    }
    if (counter > 0) {
      LOG.warn("{} usages keep their original id as they have no names index match, e.g. {}. See {}", counter, examples, file);
    }
  }

  /**
   * Released ids only remember the scientific name of their accepted name, never its id - see ReleasedId.parent.
   * The usage store on the other hand points to the accepted name by usage id, so we resolve those ids into
   * names once per canonical group to be able to compare them at all.
   *
   * @return the accepted names scientific name for a synonym, null for anything else or if it cannot be resolved
   */
  private Function<SimpleNameWithNidx, String> acceptedNames(List<? extends SimpleNameWithNidx> names, UsageMatcherStore store) {
    Map<String, String> byUsageID = null; // most canonical groups have no synonyms at all
    for (var n : names) {
      if (n.getStatus() != null && n.getStatus().isSynonym() && n.getParent() != null) {
        try {
          var accepted = store.get(n.getParent());
          if (accepted != null) {
            if (byUsageID == null) {
              byUsageID = new HashMap<>();
            }
            byUsageID.put(n.getId(), accepted.getName());
          }
        } catch (NotFoundException e) {
          // a broken tree is already reported loudly by the classification walk in UsageMatcherStore.analyze
          LOG.debug("Missing accepted name {} for synonym {}:{}", n.getParent(), mappedDatasetKey, n.getId());
        }
      }
    }
    if (byUsageID == null) {
      return NO_ACCEPTED_NAMES;
    }
    final var parents = byUsageID;
    return n -> parents.get(n.getId());
  }

  /**
   * Maps every name, OTU names excluded, to either an existing or new int based ID
   * @param canonId the canonical names index id that all names are mapped to
   * @param kept usages of the same group that already have a stable id and keep it - the base release usages of an
   *             extended release. They get no id here, but a dropped id can be redirected to them, see #recordSuperseded
   * @param acceptedNames resolves the scientific name of a synonyms accepted name, see #acceptedNames
   */
  void issueIDs(final Integer canonId, List<? extends SimpleNameWithNidx> allNames, List<? extends SimpleNameWithNidx> kept,
                Function<SimpleNameWithNidx, String> acceptedNames, Writer nomatchWriter) throws IOException {
    // OTU names (UNITE/BOLD) use their code verbatim as the stable id, regardless of names-index matching.
    // Handle them up front and exclude them from the id minting/matching below.
    final List<SimpleNameWithNidx> names = new ArrayList<>(allNames.size());
    for (var n : allNames) {
      final String otu = otuId(n);
      if (otu != null) {
        idm.mapUsage(mappedDatasetKey, n.getId(), otu);
      } else {
        names.add(n);
      }
    }
    if (canonId == null) {
      if (names.isEmpty()) {
        return;
      }
      LOG.warn("{} usages with no name match, e.g. {} - keep temporary ids", names.size(), names.get(0).getId());
      for (var n : names) {
        nomatchWriter.write(n.toStringBuilder().toString());
        nomatchWriter.write("\n");
      }

    } else {
      // the issued ids by name. By identity: names compare by value, but two equal names still need an id each
      final Map<SimpleNameWithNidx, Integer> issued = new IdentityHashMap<>();
      // which released ids do exist for this canonical names index id?
      ReleasedId[] rids = ids.byCanonId(canonId);
      if (rids != null) {
        assign(names, kept, rids, acceptedNames, issued);
      }
      // persist mappings and issue new ids for missing ones
      for (var sn : names) {
        if (!issued.containsKey(sn)) {
          issueNewId(sn, issued);
        }
        idm.mapUsage(mappedDatasetKey, sn.getId(), encode(issued.get(sn)));
      }
    }
  }

  /**
   * Hands out the released ids of one canonical group to the usages of that group, best pairing first.
   *
   * Deliberately greedy rather than a global optimum: for identifier stability the strongest pairing must be locked
   * in first and never moved off its best partner to improve some total. {@link IdCandidate} defines what "best"
   * means and is a total order, so the outcome does not depend on the order the store happens to return usages in.
   */
  private void assign(List<SimpleNameWithNidx> names, List<? extends SimpleNameWithNidx> kept, ReleasedId[] rids,
                      Function<SimpleNameWithNidx, String> acceptedNames, Map<SimpleNameWithNidx, Integer> issued) {
    // the facts are built once per side and dropped again with this group: they cache the parsed authorship, which
    // is worth having across the pairings of one group but must not be kept for every archived id of the project
    final NameIdentity.Facts[] relFacts = new NameIdentity.Facts[rids.length];
    for (int i = 0; i < rids.length; i++) {
      var r = rids[i];
      relFacts[i] = new NameIdentity.Facts(r.rank, r.authorship, r.phrase, r.status, r.code, r.group, r.parent);
    }
    final List<IdCandidate> candidates = new ArrayList<>();
    for (var n : names) {
      var facts = new NameIdentity.Facts(n, acceptedNames.apply(n));
      for (int i = 0; i < rids.length; i++) {
        var r = rids[i];
        var verdict = identity.compare(facts, relFacts[i]);
        if (verdict.isContradicted()) {
          continue; // a different name, whatever else agrees
        }
        // resurrecting an id that is not in the last release needs more than "nothing speaks against it":
        // an erroneous duplicate that was removed must not silently come back on a usage we know little about
        if (!r.isCurrent && verdict.evidence.compareTo(NameIdentity.Evidence.PLAUSIBLE) < 0) {
          continue;
        }
        candidates.add(new IdCandidate(n, r, verdict));
      }
    }
    Collections.sort(candidates);
    final IntSet taken = new IntOpenHashSet();
    for (var c : candidates) {
      if (!issued.containsKey(c.name) && !taken.contains(c.rid.id)) {
        release(c, issued);
        taken.add(c.rid.id);
      }
    }
    boolean dropping = false; // does the last release lose an id of this group at all?
    for (var r : rids) {
      dropping |= r.isCurrent && ids.containsId(r.id);
    }
    if (kept.isEmpty() || !dropping) {
      recordSuperseded(candidates, issued);
    } else {
      // usages keeping their stable id compete as the survivor of a dropped id on the same evidence as the others:
      // an extended release duplicate that is gone again because the base release carries the name now
      final Map<SimpleNameWithNidx, Integer> survivors = new IdentityHashMap<>(issued);
      final List<IdCandidate> redirects = new ArrayList<>(candidates);
      for (var n : kept) {
        final int keptId = IdConverter.LATIN29.decode(n.getId());
        survivors.put(n, keptId);
        var facts = new NameIdentity.Facts(n, acceptedNames.apply(n));
        for (int i = 0; i < rids.length; i++) {
          if (rids[i].isCurrent && rids[i].id != keptId && ids.containsId(rids[i].id)) {
            var verdict = identity.compare(facts, relFacts[i]);
            if (!verdict.isContradicted()) {
              redirects.add(new IdCandidate(n, rids[i], verdict));
            }
          }
        }
      }
      Collections.sort(redirects);
      recordSuperseded(redirects, survivors);
    }
  }

  /**
   * Works out which id took over from an id this release drops, so an old link can still be resolved instead of
   * simply going missing. This is the erroneous duplicate case: one name ends up in a release twice, the duplicate is
   * spotted and removed, and the id it had needs to point at the survivor.
   *
   * Deliberately narrow. A pair is only recorded when the dying id was in the last release, was not taken by anything
   * in this one, and some usage of its own canonical group did get an id - and then it is the usage whose evidence
   * against it ranked highest, never just any usage of the group. In an extended release the base release usages of
   * the group count as well, with the stable id they keep. An id whose every pairing was contradicted records
   * nothing: it is not the same name as what is left, so there is nothing to redirect to. A whole group disappearing
   * records nothing either.
   *
   * @param candidates all not contradicted pairings of this canonical group, best first, after the assignment
   * @param issued the ids the assignment gave the usages of this group
   */
  private void recordSuperseded(List<IdCandidate> candidates, Map<SimpleNameWithNidx, Integer> issued) {
    for (var c : candidates) {
      if (c.rid.isCurrent                        // the last release had this id
          && ids.containsId(c.rid.id)            // and nothing in this release took it
          && issued.containsKey(c.name)          // while the usage it fits best did get one
          && !superseded.containsKey(c.rid.id)   // candidates are sorted, so the first hit is the best one
      ) {
        superseded.put(c.rid.id, issued.get(c.name).intValue());
      }
    }
  }

  private void release(IdCandidate c, Map<SimpleNameWithNidx, Integer> issued){
    if (!ids.containsId(c.rid.id)) {
      throw new IllegalArgumentException("Cannot release " + c.rid.id + " which does not exist (anymore)");
    }
    ids.remove(c.rid.id);
    issued.put(c.name, c.rid.id);
    if (!c.rid.isCurrent) {
      resurrected.put(c.rid.id, c.rid.attempt);
    }
  }

  private void issueNewId(SimpleNameWithNidx n, Map<SimpleNameWithNidx, Integer> issued) {
    int id = keySequence.incrementAndGet();
    issued.put(n, id);
    created.add(id);
  }

  public void removeIdsFromDataset(int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicInteger removed = new AtomicInteger(0);
      final AtomicInteger other = new AtomicInteger(0);
      PgUtils.consume(() -> num.processIds(datasetKey, true, null), id -> {
        try {
          int intID = IdConverter.LATIN29.decode(id);
          if (ids.remove(intID) != null) {
            removed.incrementAndGet();
          } else {
            // ID exists in the dataset but was never in the xrelease archive
            // (e.g. a regular-release ID that is new in the current base release).
            // Bump keySequence past it so issueNewId() won't reassign this integer.
            keySequence.getAndUpdate(cur -> Math.max(cur, intID));
            other.incrementAndGet();
          }
          counter.incrementAndGet();
        } catch (IllegalArgumentException e) {
          // no stable id - just count
          other.incrementAndGet();
        }
      });
      LOG.info("Removed {} out of {} stable identifiers from dataset {}. Bumped keySequence for {} base-release identifiers not in xrelease archive", removed, counter, datasetKey, other);
    }
  }

  static String encode(int id) {
    return IdConverter.LATIN29.encode(id);
  }

  String otuId(SimpleName u) {
    return otuId(u, uniteVersions);
  }

  /**
   * UNITE and BOLD OTU codes are already globally stable identifiers, so we use them directly as the usage id
   * instead of minting a synthetic integer id.
   * @return the code to use as the usage id, or null if the name is not an in-scope OTU
   */
  static String otuId(SimpleName u, CountMap<String> counter) {
    if (u.getName() != null) {
      var name = u.getName();
      var m = UNITE_ID.matcher(name);
      if (m.find()) {
        if (counter != null) {
          counter.inc(m.group(2));
        }
        return name; // verbatim, e.g. SH19186714.17FU
      }
      if (BOLD_ID.matcher(name).matches()) {
        return name.replace(':', '.').toUpperCase(); // BOLD:AAA3374 -> BOLD.AAA3374
      }
    }
    return null;
  }

}
