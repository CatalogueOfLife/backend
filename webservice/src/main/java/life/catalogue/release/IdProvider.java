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
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.IdMapMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.release.ReleasedIds.ReleasedId;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  // the date we first deployed stable ids in releases - we ignore older ids than this date
  private final LocalDateTime ID_START_DATE;
  private final boolean reuseReleasedIds;

  private final int datasetKey;
  private final SqlSessionFactory factory;

  private final ReleasedIds ids = new ReleasedIds();
  private final Int2IntMap attempt2dataset = new Int2IntOpenHashMap();
  private final int currAttempt;
  private final AtomicInteger keySequence = new AtomicInteger();
  // id changes in this release
  private IntSet resurrected = new IntOpenHashSet();
  private IntSet created = new IntOpenHashSet();
  private IntSet deleted = new IntOpenHashSet();
  private IdMapMapper idm;
  private Map<TaxonomicStatus, Integer> statusOrder = Map.of(
    TaxonomicStatus.ACCEPTED, 1,
    TaxonomicStatus.PROVISIONALLY_ACCEPTED, 2,
    TaxonomicStatus.SYNONYM, 3,
    TaxonomicStatus.AMBIGUOUS_SYNONYM, 4,
    TaxonomicStatus.MISAPPLIED, 5
  );


  public static IdProvider withAllReleases(int datasetKey, int attempt, SqlSessionFactory factory) {
    return new IdProvider(datasetKey, attempt, true, null, factory);
  }

  public static IdProvider withReleasesSince(int datasetKey, int attempt, LocalDateTime since, SqlSessionFactory factory) {
    return new IdProvider(datasetKey, attempt, true, since, factory);
  }

  public static IdProvider withNoReleases(int datasetKey, int attempt, SqlSessionFactory factory) {
    return new IdProvider(datasetKey, attempt, false, null, factory);
  }

  IdProvider(int datasetKey, int attempt, boolean reuseReleasedIds, LocalDateTime ignoreOlderReleases, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.currAttempt = attempt;
    this.factory = factory;
    this.ID_START_DATE = ignoreOlderReleases;
    this.reuseReleasedIds = reuseReleasedIds;
  }

  public void run() {
    LOG.info("Map name usage IDs");
    prepare();
    mapIds();
  }

  private void prepare(){
    // populate ids from db
    LOG.info("Prepare stable id provider");
    if (reuseReleasedIds) {
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        DatasetSearchRequest dsr = new DatasetSearchRequest();
        dsr.setReleasedFrom(datasetKey);
        dsr.setSortBy(DatasetSearchRequest.SortBy.CREATED);
        dsr.setReverse(true);
        List<Dataset> releases = dm.search(dsr, null, new Page(0, 100));
        for (Dataset rel : releases) {
          if (ID_START_DATE != null && rel.getCreated().isBefore(ID_START_DATE)) {
            LOG.info("Ignore old release {} with unstable ids", rel.getKey());
            continue;
          }
          AtomicInteger counter = new AtomicInteger();
          int attempt = rel.getImportAttempt();
          attempt2dataset.put(attempt, (int)rel.getKey());
          session.getMapper(NameUsageMapper.class).processNxIds(rel.getKey()).forEach(sn -> {
            counter.incrementAndGet();
            if (sn.getNameIndexId() == null) {
              LOG.info("Existing release id {}:{} without a names index id. Skip!", rel.getKey(), sn.getId());
            } else {
              ids.add(new ReleasedIds.ReleasedId(sn, attempt));
            }
          });
          LOG.info("Read {} usages from previous release {}, key={}. Total released ids = {}", counter, rel.getImportAttempt(), rel.getKey(), ids.size());
        }
      }
    }
    keySequence.set(ids.maxKey());
    LOG.info("Max existing id = {} ({})", keySequence, encode(keySequence.get()));
  }

  private void mapIds(){
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession readSession = factory.openSession(true);
         SqlSession writeSession = factory.openSession(false)
    ) {
      idm = writeSession.getMapper(IdMapMapper.class);
      final int batchSize = 10000;
      Integer lastCanonID = null;
      List<SimpleNameWithNidx> group = new ArrayList<>();
      for (SimpleNameWithNidx u : readSession.getMapper(NameUsageMapper.class).processNxIds(datasetKey)) {
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
    deleted = ids.remainingIds(currAttempt);
  }

  private void mapCanonicalGroup(List<SimpleNameWithNidx> group){
    // make sure we have the names sorted by their nidx
    group.sort(Comparator.comparing(SimpleNameWithNidx::getNameIndexId));
    // now split the canonical group into subgroups for each nidx to match them individually
    for (List<SimpleNameWithNidx> idGroup : IterUtils.group(group, Comparator.comparing(SimpleNameWithNidx::getNameIndexId))) {
      issueIDs(idGroup.get(0).getNameIndexId(), idGroup);
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
        idm.mapUsage(datasetKey, sn.getId(), encode(sn.getCanonicalId()));
      }
    }
  }

  private void release(ScoreMatrix.ReleaseMatch rm, ScoreMatrix scores){
    rm.name.setCanonicalId(rm.rid.id);
    ids.remove(rm.rid.id);
    if (rm.rid.attempt != currAttempt) {
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
    return IdConverter.LATIN32.encode(id);
  }

}
