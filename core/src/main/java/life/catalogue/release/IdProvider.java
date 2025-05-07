package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.collection.Int2IntBiMap;
import life.catalogue.common.collection.IterUtils;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.release.ReleasedIds.ReleasedId;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static life.catalogue.api.vocab.TaxonomicStatus.MISAPPLIED;

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
 * 1) Generate a ReleasedIds view on all previous releases,
 *    keyed on their usage id and names index id (nxId).
 *    For each id only use the version from its latest release.
 *    Include ALL ids, also deleted ones.
 *    Convert ids to their int representation to save memory and simplify comparison etc.
 *    Expose only properties needed for matching, i.e. id (int), nxId (int), status, parentID (int), ???
 *
 * 2) Process all name usages as groups by their canonical name index id, i.e. all usages that share the same name regardless of
 *    their authorship. Process groups by ranks from top down (allows to compare parentIds).
 *
 * 3) Match all usages in such a group ordered by their status:
 *    First assign ids for accepted, then prov accepted, synonyms, ambiguous syns and finally misapplied names
 */
public class IdProvider {
  protected final Logger LOG = LoggerFactory.getLogger(IdProvider.class);

  private final int projectKey;
  private final int attempt;
  private final DatasetOrigin origin;
  private final int releaseDatasetKey;
  private final Integer lastReleaseKey;
  private final SqlSessionFactory factory;
  private final ReleaseConfig cfg;
  private final ReleasedIds ids;
  private final Int2IntBiMap dataset2attempt = new Int2IntBiMap();
  private final Int2ObjectMap<Release> dataset2release = new Int2ObjectOpenHashMap<>();
  private final List<Integer> ignoredReleases = new ArrayList<>();
  private final IntList additionalReleases = new IntArrayList();
  private final AtomicInteger keySequence = new AtomicInteger();
  private final File reportDir;
  // id changes in this release
  private int reused = 0;
  private IntSet created = new IntOpenHashSet();
  private Int2IntMap deleted = new Int2IntOpenHashMap(); // maps to release attempt for reporting!
  private Int2IntMap resurrected = new Int2IntOpenHashMap(); // maps to release attempt for reporting!
  private final SortedMap<String, List<InstableName>> unstable = new TreeMap<>();
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
  public IdProvider(int projectKey, DatasetOrigin origin, int attempt, int releaseDatasetKey, ReleaseConfig cfg, SqlSessionFactory factory) {
    this.releaseDatasetKey = releaseDatasetKey;
    this.projectKey = projectKey;
    this.origin = origin;
    this.attempt = attempt;
    this.factory = factory;
    this.cfg = cfg;
    reportDir = cfg.reportDir(projectKey, attempt);
    reportDir.mkdirs();
    dataset2attempt.put(releaseDatasetKey, attempt);
    // load a map of all releases to their attempts and figure out last release
    lastReleaseKey = loadReleaseAttempts();
    // now build the main release identifier store
    ids = new ReleasedIds(lastReleaseKey == null ? null : dataset2attempt.getValue(lastReleaseKey));
    // create report dirs
    File dir = cfg.reportDir(projectKey, attempt);
    dir.mkdirs();

    if (cfg.restart != null) {
      LOG.info("Use ID provider with no previous IDs. Start ID sequence with {} ({})", cfg.restart, encode(cfg.restart));
      keySequence.set(cfg.restart);

    } else {
      // populate ids from db
      loadPreviousReleaseIds();
      LOG.info("Last release attempt={} with {} IDs", ids.getLastAttempt(), ids.lastAttemptIdCount());
      keySequence.set(ids.maxKey());
      LOG.info("Max existing id = {}. Start ID sequence with {} ({})", ids.maxKey(), keySequence, encode(keySequence.get()));
    }
  }

  /**
   * @return the key that will be issued next
   */
  public int previewNextKey() {
    return keySequence.get();
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

    IdReport(IntSet created, Int2IntMap deleted, Int2IntMap resurrected) {
      this.created = created;
      this.deleted = deleted;
      this.resurrected = resurrected;
    }
  }

  public IdReport getReport() {
    return new IdReport(created, deleted, resurrected);
  }

  protected void report() {
    try {
      File dir = cfg.reportDir(projectKey, attempt);
      // read the following IDs from previous releases
      reportFile(dir,"deleted.tsv", deleted.keySet(), deleted, true);
      reportFile(dir,"resurrected.tsv", resurrected.keySet(), deleted, false);
      // read ID from this release & ID mapping
      reportFile(dir,"created.tsv", created, id -> -1, false);
      // clear instable names, removing the ones with just deletions
      unstable.entrySet().removeIf(entry -> entry.getValue().parallelStream().allMatch(n -> n.del));
      final var unstableFile = new File(dir, "unstable.txt");
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
    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project "+projectKey, e);
    }
    LOG.info("ID provision done. Reused {} stable IDs for project release {}-{} ({}), resurrected={}, newly created={}, deleted={}", reused, projectKey, attempt, releaseDatasetKey, resurrected.size(), created.size(), deleted.size());
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
        writer.write(match.getName().getKey());
        writer.write('/');
        writer.write(match.getName().getCanonicalId());
        writer.write(' ');
        writer.write(String.valueOf(match.getType()));
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

  private void reportFile(File dir, String filename, IntSet ids, Int2IntFunction attemptLookup, boolean deletion) throws IOException {
    File f = new File(dir, filename);
    try(TabWriter tsv = TabWriter.fromFile(f);
        SqlSession session = factory.openSession(true)
    ) {
      num = session.getMapper(NameUsageMapper.class);
      LOG.info("Writing ID report for project release {}-{} of {} IDs to {}", projectKey, attempt, ids.size(), f);
      ids.intStream()
        .sorted()
        .forEach(id -> reportId(id, attemptLookup.get(id), tsv, deletion));
    }
  }

  /**
   * @param attempt if larger than 0 it was issued in an older release before, otherwise it is new and look it up in the project using the id map table
   * @param deletion
   */
  private void reportId(int id, int attempt, TabWriter tsv, boolean deletion){
    String ID = IdConverter.LATIN29.encode(id);
    SimpleName sn = null;
    DSID<String> key = null;
    try {
      int datasetKey = -1;
      if (attempt>0) {
        datasetKey = dataset2attempt.getKey(attempt);
        key = DSID.of(datasetKey, ID);
        sn = num.getSimple(key);
      } else {
        // usages do not exist yet in the release - we gotta use the id map and look them up in the project!
        sn = num.getSimpleByIdMap(DSID.of(projectKey, ID));
        if (sn != null) {
          key = DSID.of(releaseDatasetKey, ID);
        }
      }

      if (sn == null) {
        if (attempt>0) {
          LOG.warn("Old ID {}-{} [{}] reported without name usage from attempt {}", datasetKey, ID, id, attempt);
        } else {
          LOG.warn("ID {} [{}] reported without name usage", ID, id);
        }
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
      LOG.error("Failed to report {}ID {}: {} [key={}, sn={}]", attempt>0 ? "old ":"", id, ID, key, sn, e);
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
    if (cfg.ignoredReleases != null && cfg.ignoredReleases.containsKey(projectKey)) {
      ignoredReleases.addAll(cfg.ignoredReleases.get(projectKey));
    }
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      for (DatasetOrigin o : List.of(DatasetOrigin.RELEASE, DatasetOrigin.XRELEASE)) {
        var rkey = dm.latestRelease(projectKey, true, ignoredReleases, o);
        if (rkey != null) {
          if (o == origin) {
            lrkey = rkey;
          } else {
            LOG.info("Add previous {} {} to list of releases to load", o, rkey);
            additionalReleases.add(rkey);
          }
        }
      }
      // we now load all known release attempts as the usage archive can contain any of them
      dm.listReleasesQuick(projectKey).forEach(d -> {
        dataset2release.put(d.getKey(), new Release(d.getKey(), d.getOrigin(), d.getAttempt()));
        if (d.getKey() != releaseDatasetKey) {
          if (ignoredReleases.contains(d.getKey())) {
            LOG.info("Configured to ignore release {}", d.getKey());
          } else {
            dataset2attempt.put(d.getKey(), d.getAttempt());
          }
        }
      });
      LOG.info("Found {} relevant past releases", dataset2attempt.size());
    }
    return lrkey;
  }

  static class LoadStats {
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger nomatches = new AtomicInteger();
    AtomicInteger temporary = new AtomicInteger();

    @Override
    public String toString() {
      return String.format("%s usages with %s temporary ids and %s missing matches ", counter, nomatches, temporary);
    }
  }

  /**
   * Loads all ever issued identifiers for this project, preferring the latest version of any id.
   * It starts by loading the entire last public release and then adds on all archived names that have been used in earlier releases,
   * even if their dataset has been deleted by now.
   */
  @VisibleForTesting
  protected void loadPreviousReleaseIds(){
    try (SqlSession session = factory.openSession(true)) {
      // load entire last release
      if (lastReleaseKey == null) {
        LOG.info("There has been no previous {}, start without existing ids", origin);

      } else {
        final LoadStats stats = new LoadStats();
        PgUtils.consume(
          () -> session.getMapper(NameUsageMapper.class).processNxIds(lastReleaseKey),
          sn -> addReleaseId(lastReleaseKey, origin, sn, stats)
        );
        LOG.info("Read {} from last {} {}. Total ids={}", stats, origin, lastReleaseKey, ids.size());
      }

      // load additional releases if configured - used to read both regular and extended releases
      for (var rk : additionalReleases) {
        final LoadStats stats = new LoadStats();
        PgUtils.consume(
          () -> session.getMapper(NameUsageMapper.class).processNxIds(rk),
          sn -> addReleaseId(rk, dataset2release.get(rk).origin, sn, stats)
        );
        LOG.info("Read {} from past release {}. Total ids={}", stats, rk, ids.size());
      }

      // always also include the archived names if they have not been processed before yet
      final int sizeBefore = ids.size();
      final LoadStats stats = new LoadStats();
      LOG.info("Add archived names");
      PgUtils.consume(
        () -> session.getMapper(ArchivedNameUsageMapper.class).processArchivedUsages(projectKey),
        sn -> addReleaseId(sn.getLastReleaseKey(), null, sn, stats)
      );
      LOG.info("Read {} from archived names. Adding {} previously used ids to a total of {}", stats, ids.size() - sizeBefore, ids.size());
      //ids.log();
    }
  }

  /**
   * @param sn simple name with parent being a scientificName, not ID!
   */
  @VisibleForTesting
  protected void addReleaseId(int releaseDatasetKey, DatasetOrigin origin, SimpleNameWithNidx sn, LoadStats stats){
    stats.counter.incrementAndGet();
    if (sn.getNamesIndexId() == null) {
      stats.nomatches.incrementAndGet();
      LOG.info("Existing release id {}:{} without a names index id. Skip {}", releaseDatasetKey, sn.getId(), sn.getLabel());
    } else {
      try {
        var rl = ReleasedId.create(sn, dataset2attempt.getValue(releaseDatasetKey), origin == this.origin);
        ids.add(rl);
        LOG.debug("Add {} from {}/{}: {}", sn.getId(), rl.attempt, releaseDatasetKey, sn);
      } catch (IllegalArgumentException e) {
        // expected for temp identifiers, swallow and count
        stats.temporary.incrementAndGet();
      }
    }
  }

  @VisibleForTesting
  protected Int2IntBiMap getDatasetAttemptMap(){
    return dataset2attempt;
  }

  protected Writer buildNomatchWriter() throws IOException {
    return UTF8IoUtils.writerFromFile(new File(reportDir, "nomatch.txt"));
  }

  protected void mapIds(){
    try (SqlSession readSession = factory.openSession(true)) {
      mapIds(readSession.getMapper(NameUsageMapper.class).processNxIds(projectKey));
    }
  }

  @VisibleForTesting
  protected void mapIds(Iterable<SimpleNameWithNidx> names){
    LOG.info("Map name usage IDs");
    final int lastRelIds = ids.lastAttemptIdCount();
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession writeSession = factory.openSession(false);
         Writer nomatchWriter = buildNomatchWriter()
    ) {
      idm = writeSession.getMapper(IdMapMapper.class);
      final int batchSize = 10000;
      Integer lastCanonID = null;
      List<SimpleNameWithNidx> group = new ArrayList<>();
      for (SimpleNameWithNidx u : names) {
        if (!Objects.equals(lastCanonID, u.getCanonicalId()) && !group.isEmpty()) {
          mapCanonicalGroup(group, nomatchWriter);
          int before = counter.get() / batchSize;
          int after = counter.addAndGet(group.size()) / batchSize;
          if (before != after) {
            writeSession.commit();
          }
          group.clear();
        }
        lastCanonID = u.getCanonicalId();
        group.add(u);
      }
      mapCanonicalGroup(group, nomatchWriter);
      writeSession.commit();

    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project " + projectKey, e);
    }
    // ids remaining from the current attempt will be deleted
    deleted = ids.lastAttemptIds();
    reused = lastRelIds - deleted.size();
    LOG.info("Done mapping name usage IDs. {} ids from the last release will be deleted, {} have been reused.", deleted.size(), reused);
  }

  private void mapCanonicalGroup(List<SimpleNameWithNidx> group, Writer nomatchWriter) throws IOException {
    if (!group.isEmpty()) {
      // workaround for names index duplicates bug
      if (cfg.nidxDeduplication) {
        removeDuplicateIdxEntries(group);
      }

      // make sure we have the names sorted by their nidx
      group.sort(Comparator.comparing(SimpleNameWithNidx::getNamesIndexId, nullsLast(naturalOrder())));
      // now split the canonical group into subgroups for each nidx to match them individually
      for (List<SimpleNameWithNidx> idGroup : IterUtils.group(group, Comparator.comparing(SimpleNameWithNidx::getNamesIndexId, nullsLast(naturalOrder())))) {
        issueIDs(idGroup.get(0).getNamesIndexId(), idGroup, nomatchWriter, true);
      }
    }
  }

  /**
   * A temporary "hack" to remove the redundant names index entries that get created by the current NamesIndex implementation.
   * It reduces the names with the exact same name & authorship to a single index id (the lowest).
   */
  private void removeDuplicateIdxEntries(List<SimpleNameWithNidx> group) {
    // first determine which is the lowest nidx for each full name
    Set<Integer> originalIds = new HashSet<>();
    Object2IntMap<String> name2nidx = new Object2IntOpenHashMap<>();
    for (SimpleNameWithNidx n : group) {
      originalIds.add(n.getNamesIndexId());
      if (n.getNamesIndexId() == null) continue;
      String label = n.getLabel().toLowerCase();
      name2nidx.putIfAbsent(label, n.getNamesIndexId());
      if (name2nidx.get(label)>n.getNamesIndexId()) {
        name2nidx.put(label, n.getNamesIndexId());
      }
    }
    if (originalIds.size() != name2nidx.size()) {
      LOG.info("Reducing canonical group {} with {} distinct nidx values to {}", group.get(0).getName(), originalIds.size(), name2nidx.size());
      // now update redundant nidx
      for (SimpleNameWithNidx n : group) {
        String label = n.getLabel().toLowerCase();
        if (name2nidx.containsKey(label) && (n.getNamesIndexId() == null || name2nidx.getInt(label) != n.getNamesIndexId())) {
          LOG.debug("Updated names index match from {} to {} for {}", n.getNamesIndexId(), name2nidx.getInt(label), label);
          n.setNamesIndexId(name2nidx.getInt(label));
        }
      }
    }
  }

  /**
   * Populates sn.canonicalId with either an existing or new int based ID
   * @param nidx the concrete names index id that all names are mapped to
   */
  protected void issueIDs(Integer nidx, List<SimpleNameWithNidx> names, Writer nomatchWriter, boolean persistIdMapping) throws IOException {
    if (nidx == null) {
      LOG.warn("{} usages with no name match, e.g. {} - keep temporary ids", names.size(), names.get(0).getId());
      for (SimpleNameWithNidx n : names) {
        nomatchWriter.write(n.toStringBuilder().toString());
        nomatchWriter.write("\n");
      }

    } else {
      // convenient "hack": we keep the new identifiers as the canonicalID property of SimpleNameWithNidx
      names.forEach(n->n.setCanonicalId(null));
      // how many released ids do exist for this names index id?
      ReleasedId[] rids = ids.byNxId(nidx);
      if (rids != null) {
        IntSet ids = new IntOpenHashSet();
        ScoreMatrix scores = new ScoreMatrix(names, rids, IdProvider::matchScore);
        List<ScoreMatrix.ReleaseMatch> best = scores.highest();
        while (!best.isEmpty()) {
          // best is sorted, issue as they come but avoid already released ids
          for (ScoreMatrix.ReleaseMatch m : best) {
            if (!ids.contains(m.rid.id)) {
              release(m, scores);
              ids.add(m.rid.id);
            }
          }
          best = scores.highest();
        }
      }
      // persist mappings, issuing new ids for missing ones
      for (SimpleNameWithNidx sn : names) {
        if (sn.getCanonicalId() == null) {
          issueNewId(sn);
        }
        if (persistIdMapping) {
          idm.mapUsage(projectKey, sn.getId(), encode(sn.getCanonicalId()));
        }
      }
    }
  }

  private void release(ScoreMatrix.ReleaseMatch rm, ScoreMatrix scores){
    if (!ids.containsId(rm.rid.id)) {
      throw new IllegalArgumentException("Cannot release " + rm.rid.id + " which does not exist (anymore)");
    }
    var rid = ids.remove(rm.rid.id);
    rm.name.setCanonicalId(rm.rid.id);
    if (rm.rid.attempt != ids.getLastAttempt()) {
      resurrected.put(rm.rid.id, rm.rid.attempt);
    }
    scores.remove(rm);
  }

  private void issueNewId(SimpleNameWithNidx n) {
    int id = keySequence.incrementAndGet();
    n.setCanonicalId(id);
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
          }
          counter.incrementAndGet();
        } catch (IllegalArgumentException e) {
          // no stable id - just count
          other.incrementAndGet();
        }
      });
      LOG.info("Removed {} out of {} stable identifiers from dataset {}. Ignored {} other unstable identifiers", removed, counter, datasetKey, other);
    }
  }

  /**
   * For homonyms or names very much alike we must provide a deterministic rule
   * that selects a stable id based on all previous releases.
   *
   * This can happen due to real homonyms, erroneous duplicates in the data
   * or potentially extensive pro parte synonyms as we have now for some genera like Achorutini Börner, C, 1901.
   *
   * For synonyms we evaluate the accepted name.
   * This helps with sticky ids for pro parte synonyms.
   *
   * @return zero for no match, positive for a match. The higher the better!
   */
  private static int matchScore(SimpleNameWithNidx n, ReleasedId r) {
    // only one is a misapplied name - never match to anything else
    if (!Objects.equals(n.getStatus(), r.status) && (n.getStatus()==MISAPPLIED || r.status==MISAPPLIED) ) {
      return 0;
    }

    int score = 1;
    // exact same status
    if (Objects.equals(n.getStatus(), r.status)) {
      score += 5;
    }
    // rank
    if (Objects.equals(n.getRank(), r.rank)) {
      score += 10;
    }
    // parent for synonyms
    if (n.getStatus() != null && n.getStatus().isSynonym()) {
      // block synonyms with different accepted names aka parent
      if (StringUtils.equalsIgnoreCase(n.getParent(), r.parent)) {
        score += 6;
      }
    }
    // match type
    score += matchTypeScore(n.getNamesIndexMatchType());
    score += matchTypeScore(r.matchType);

    // exact same authorship
    if (StringUtils.equalsDigitOrAsciiLettersIgnoreCase(n.getAuthorship(), r.authorship)) {
      score += 6;
    }
    // name phrase is key for misapplied names!
    if (StringUtils.equalsDigitOrAsciiLettersIgnoreCase(n.getPhrase(), r.phrase)) {
      score += 5;
    } else if (n.getStatus() == MISAPPLIED) {
      return 0;
    }

    return score;
  }

  private static int matchTypeScore(MatchType mt) {
    switch (mt) {
      case EXACT: return 3;
      case VARIANT: return 2;
      case CANONICAL: return 1;
      default: return 0;
    }
  }

  static String encode(int id) {
    return IdConverter.LATIN29.encode(id);
  }

}
