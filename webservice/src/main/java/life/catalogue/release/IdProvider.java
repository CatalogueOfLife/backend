package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.collection.IterUtils;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.text.StringUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.IdMapMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.release.ReleasedIds.ReleasedId;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
 * 1) Generate a ReleasedIds view (use interface to allow for different impls) on all previous releases,
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
  private final SqlSessionFactory factory;
  private final ReleaseConfig cfg;
  private final ReleasedIds ids = new ReleasedIds();
  private final Int2IntMap attempt2dataset = new Int2IntOpenHashMap();
  private final AtomicInteger keySequence = new AtomicInteger();
  // id changes in this release
  private int reused = 0;
  private IntSet resurrected = new IntOpenHashSet();
  private IntSet created = new IntOpenHashSet();
  private IntSet deleted = new IntOpenHashSet();
  private IdMapMapper idm;
  private NameUsageMapper num;
  private Map<TaxonomicStatus, Integer> statusOrder = Map.of(
    TaxonomicStatus.ACCEPTED, 1,
    TaxonomicStatus.PROVISIONALLY_ACCEPTED, 2,
    TaxonomicStatus.SYNONYM, 3,
    TaxonomicStatus.AMBIGUOUS_SYNONYM, 4,
    TaxonomicStatus.MISAPPLIED, 5
  );

  public IdProvider(int projectKey, int attempt, ReleaseConfig cfg, SqlSessionFactory factory) {
    this.projectKey = projectKey;
    this.attempt = attempt;
    this.factory = factory;
    this.cfg = cfg;
  }

  public void run() {
    prepare();
    mapIds();
    report();
    LOG.info("Reused {} stable IDs for project release {}-{}, resurrected={}, newly created={}, deleted={}", projectKey, attempt, reused, resurrected.size(), created.size(), deleted.size());
  }

  private void report() {
    try {
      File dir = cfg.reportDir(projectKey, attempt);
      dir.mkdirs();
      reportFile(dir,"deleted.tsv", deleted, true); // read ID from older releases
      reportFile(dir,"created.tsv", created, false); // read ID from this release & ID mapping
      reportFile(dir,"resurrected.tsv", deleted, false); // read ID from older releases
    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project "+projectKey, e);
    }
  }

  private void reportFile(File dir, String filename, IntSet ids, boolean useOldReleases) throws IOException {
    File f = new File(dir, filename);
    try(TabWriter tsv = TabWriter.fromFile(f);
        SqlSession session = factory.openSession(true)
    ) {
      idm = session.getMapper(IdMapMapper.class);
      num = session.getMapper(NameUsageMapper.class);
      LOG.info("Writing ID report for project release {}-{} of {} IDs to {}", projectKey, attempt, ids.size(), f);
      ids.stream()
        .sorted()
        .forEach(id -> reportId(id, useOldReleases, tsv));
    }
  }

  private void reportId(int id, boolean isOld, TabWriter tsv){
    try {
      String ID = IdConverter.LATIN29.encode(id);
      SimpleName sn;
      if (isOld) {
        ReleasedId rid = this.ids.byId(id);
        int datasetKey = attempt2dataset.get(rid.attempt);
        sn = num.getSimple(DSID.of(datasetKey, ID));
      } else {
        // usages do not exist yet in the release - we gotta use the id map and look them up in the project!
        sn = num.getSimpleByIdMap(DSID.of(projectKey, ID));
      }
      tsv.write(new String[]{ID, sn.getRank().toString(), sn.getStatus().toString(), sn.getName(), sn.getAuthorship()});

    } catch (IOException e) {
      LOG.error("Failed to write ID report for {}", id, e);
    }
  }

  private void prepare(){
    if (cfg.restart) {
      LOG.info("Use ID provider with no previous IDs");

    // populate ids from db
    } else {
      LOG.info("Use ID provider with stable IDs since {}", cfg.since);
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        DatasetSearchRequest dsr = new DatasetSearchRequest();
        dsr.setReleasedFrom(projectKey);
        List<Dataset> releases = dm.search(dsr, null, new Page(0, 1000));
        releases.sort(Comparator.comparing(Dataset::getImportAttempt).reversed());
        for (Dataset rel : releases) {
          if (cfg.since != null && rel.getCreated().isBefore(cfg.since)) {
            LOG.info("Ignore old release {} with unstable ids", rel.getKey());
            continue;
          }
          AtomicInteger counter = new AtomicInteger();
          int attempt = rel.getImportAttempt();
          attempt2dataset.put(attempt, (int)rel.getKey());
          session.getMapper(NameUsageMapper.class).processNxIds(rel.getKey()).forEach(sn -> {
            counter.incrementAndGet();
            if (sn.getNamesIndexId() == null) {
              LOG.info("Existing release id {}:{} without a names index id. Skip!", rel.getKey(), sn.getId());
            } else {
              ids.add(new ReleasedIds.ReleasedId(sn, attempt));
            }
          });
          LOG.info("Read {} usages from previous release {}, key={}. Total released ids = {}", counter, rel.getImportAttempt(), rel.getKey(), ids.size());
        }
      }
      LOG.info("Last release attempt={} with {} IDs", ids.getMaxAttempt(), ids.maxAttemptIdCount());

    }
    keySequence.set(Math.max(cfg.start, ids.maxKey()));
    LOG.info("Max existing id = {}. Start ID sequence with {} ({})", ids.maxKey(), keySequence, encode(keySequence.get()));
  }

  private void mapIds(){
    LOG.info("Map name usage IDs");
    final int lastRelIds = ids.maxAttemptIdCount();
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession readSession = factory.openSession(true);
         SqlSession writeSession = factory.openSession(false)
    ) {
      idm = writeSession.getMapper(IdMapMapper.class);
      final int batchSize = 10000;
      Integer lastCanonID = null;
      List<SimpleNameWithNidx> group = new ArrayList<>();
      for (SimpleNameWithNidx u : readSession.getMapper(NameUsageMapper.class).processNxIds(projectKey)) {
        if (lastCanonID != null && !lastCanonID.equals(u.getCanonicalId())) {
          mapCanonicalGroup(group);
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
      mapCanonicalGroup(group);
      writeSession.commit();
    }
    // ids remaining from the current attempt will be deleted
    deleted = ids.maxAttemptIds();
    reused = lastRelIds - deleted.size();
  }

  private void mapCanonicalGroup(List<SimpleNameWithNidx> group){
    // make sure we have the names sorted by their nidx
    group.sort(Comparator.comparing(SimpleNameWithNidx::getNamesIndexId));
    // now split the canonical group into subgroups for each nidx to match them individually
    for (List<SimpleNameWithNidx> idGroup : IterUtils.group(group, Comparator.comparing(SimpleNameWithNidx::getNamesIndexId))) {
      issueIDs(idGroup.get(0).getNamesIndexId(), idGroup);
    }
  }

  private void issueIDs(Integer nidx, List<SimpleNameWithNidx> names) {
    if (nidx == null) {
      LOG.info("{} usages with no name match, e.g. {} - keep temporary ids", names.size(), names.get(0).getId());

    } else {
      // convenient "hack": we keep the new identifiers as the canonicalID property of SimpleNameWithNidx
      names.forEach(n->n.setCanonicalId(null));
      // how many released ids do exist for this names index id?
      ReleasedId[] rids = ids.byNxId(nidx);
      if (rids != null) {
        ScoreMatrix scores = new ScoreMatrix(names, ids.byNxId(nidx), this::matchScore);
        List<ScoreMatrix.ReleaseMatch> best = scores.highest();
        while (!best.isEmpty()) {
          if (best.size()==1) {
            release(best.get(0), scores);
          } else {
            // TODO: equal high scores, but potentially non conflicting names. Resolve
            //System.out.println(best);
          }
          best = scores.highest();
        }
      }
      // persist mappings, issuing new ids for missing ones
      for (SimpleNameWithNidx sn : names) {
        if (sn.getCanonicalId() == null) {
          issueNewId(sn);
        }
        idm.mapUsage(projectKey, sn.getId(), encode(sn.getCanonicalId()));
      }
    }
  }

  private void release(ScoreMatrix.ReleaseMatch rm, ScoreMatrix scores){
    rm.name.setCanonicalId(rm.rid.id);
    ids.remove(rm.rid.id);
    if (rm.rid.attempt != ids.getMaxAttempt()) {
      resurrected.add(rm.rid.id);
    }
    scores.remove(rm);
  }

  private void issueNewId(SimpleNameWithNidx n) {
    int id = keySequence.incrementAndGet();
    n.setCanonicalId(id);
    created.add(id);
  }

  /**
   * @return zero for no match, positive for a match. The higher the better!
   */
  private int matchScore(SimpleName n, ReleasedId r) {
    SimpleName rn = load(r);
    return 1;
  }

  private SimpleName load(ReleasedId rid) {
    return null;
  }

  /**
   * Takes several existing ids as candidates and checks whether one matches the given simple name.
   * It is assumed all ids match the canonical name at least.
   * Selecting considers authorship and the parent name to select between multiple candidates.
   *
   * @param candidates id candidates from any previous release (but not from the current project)
   * @param nu the name usage to match against
   * @return the matched id or -1 for no match
   */
  private ReleasedId selectID(ReleasedId[] candidates, SimpleName nu) {
    if (candidates.length == 1) return candidates[0];
    // select best id from multiple
    int max = 0;
    List<ReleasedId> best = new ArrayList<>();
    for (ReleasedId rid : candidates) {
      DSID<String> key = DSID.of(attempt2dataset.get(rid.attempt), encode(rid.id));
      SimpleName sn = null;
      //TODO: make sure misapplied names never match non misapplied names
      // names index ids are for NAMES only, not for usage related namePhrase & accordingTo !!!
      int score = 0;
      // author 0-2
      if (nu.getAuthorship() != null && Objects.equals(nu.getAuthorship(), sn.getAuthorship())) {
        score += 2;
      } else if (StringUtils.equalsIgnoreCaseAndSpace(nu.getAuthorship(), sn.getAuthorship())) {
        score += 1;
      }
      // parent 0-2
      if (nu.getParent() != null && Objects.equals(nu.getParent(), sn.getParent())) {
        score += 2;
      }
      if (score > max) {
        max = score;
        best.clear();
        best.add(rid);
      } else if (score == max) {
        best.add(rid);
      }
    }
    if (max > 0) {
      if (best.size() > 1) {
        LOG.debug("{} ids all matching to {}", Arrays.toString(best.toArray()), nu);
      }
      return best.get(0);
    }
    return null;
  }

  static String encode(int id) {
    return IdConverter.LATIN29.encode(id);
  }

}
