package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.TaxonomicStatus;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a usage id mapping table that maps the temporary UUIDs from the project source
 * to some stable integer based identifiers. The newly generated table can be used in copy dataset commands
 * during the project release.
 *
 * Prerequisites:
 *     - nomCode applied to all usages
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
 * 2) Keep existing non uuids, verify they still match with their nxId or exact sciname tp prevent nx bugs
 *
 * 3) Match all usages with temp UUID, ordered for matching by their status, then rank from top down (allows to compare parentIds):
 *    First assign ids for accepted, then prov accepted, synonyms, ambiguous syns and finally misapplied names
 *    -
 * 4) Use new "usage" matching service that sits on top of the ReleaseView
 *    - retrieve candidates by looking up all ids by their nxId
 *    - if none, issue a new id
 *    - if single match, not yet taken: use it
 *    - if single and taken, issue a new one and log this (in issues, reports and logs???)
 *    - if multiple look for best match and use it
 */
public class StableIdProvider {
  protected final Logger LOG = LoggerFactory.getLogger(StableIdProvider.class);

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
  private NameUsageMapper num;
  private IdMapMapper idm;

  public StableIdProvider(int datasetKey, int attempt, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.currAttempt = attempt;
    this.factory = factory;
  }

  public void run() {
    LOG.info("Map name usage IDs");
    prepare();
    mapIds();
  }

  private void prepare(){
    // populate ids from db
    LOG.info("Prepare stable id provider");
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest dsr = new DatasetSearchRequest();
      dsr.setReleasedFrom(datasetKey);
      dsr.setSortBy(DatasetSearchRequest.SortBy.CREATED);
      dsr.setReverse(true);
      List<Dataset> releases = dm.search(dsr, null, new Page(0, 100));
      for (Dataset rel : releases) {
        AtomicInteger counter = new AtomicInteger();
        int attempt = rel.getImportAttempt();
        attempt2dataset.put(attempt, (int)rel.getKey());
        session.getMapper(NameUsageMapper.class).processNxIds(rel.getKey(), null).forEach(sn -> {
          counter.incrementAndGet();
          ids.add(new ReleasedIds.ReleasedId(decode(sn.getId()), sn.getNameIndexIds().toIntArray(), attempt));
        });
        LOG.info("Read {} usages from previous release {}, key={}. Total released ids = {}", counter, rel.getImportAttempt(), rel.getKey(), ids.size());
      }
    }
    keySequence.set(ids.maxKey());
    LOG.info("Max existing id is {} ({})", keySequence, encode(keySequence.get()));
  }

  private void mapIds(){
    // we keep a list of usages that have ambiguous matches to multiple index names and deal with those names last.
    DSID<String> key = DSID.of(datasetKey, "");
    for (TaxonomicStatus status : new TaxonomicStatus[]{
      TaxonomicStatus.ACCEPTED,
      TaxonomicStatus.PROVISIONALLY_ACCEPTED,
      TaxonomicStatus.SYNONYM,
      TaxonomicStatus.AMBIGUOUS_SYNONYM,
      TaxonomicStatus.MISAPPLIED
    }) {
      AtomicInteger counter = new AtomicInteger();
      List<SimpleNameWithNidx> ambiguous = new ArrayList<>();
      try (SqlSession session = factory.openSession(false)) {
        idm = session.getMapper(IdMapMapper.class);
        num = session.getMapper(NameUsageMapper.class);
        num.processNxIds(datasetKey, status).forEach(u -> {
          if (u.getNameIndexIds().isEmpty()) {
            LOG.debug("{} usage {} with no name match id - keep the temporary id", status, u.getId());
          } else if (u.getNameIndexIds().size() == 1) {
            String id = issueID(u, u.getNameIndexIds().toIntArray());
            idm.mapUsage(datasetKey, u.getId(), id);
            if (counter.incrementAndGet() % 10000 == 0) {
              session.commit();
            }
          } else {
            ambiguous.add(u);
          }
        });
        session.commit();

        LOG.info("Updated {} ids for simple matches, doing {} ambiguous matches next", counter, ambiguous.size());
        for (SimpleNameWithNidx u : ambiguous){
          String id = issueID(u, u.getNameIndexIds().toIntArray());
          idm.mapUsage(datasetKey, u.getId(), id);
          if (counter.incrementAndGet() % 10000 == 0) {
            session.commit();
          }
        }
        session.commit();
      }
    }
  }

  private String issueID(SimpleName name, int... nidxs) {
    int id;
    // does a previous id match?
    for (int nidx : nidxs) {
      ReleasedId[] rids = ids.byNxId(nidx);
      if (rids != null) {
        ReleasedId rid = selectID(rids, name);
        if (rid != null) {
          if (rid.attempt != currAttempt) {
            resurrected.add(rid.id);
          }
          ids.remove(rid.id);
          return rid.id();
        }
      }
    }
    // if nothing found, issue a new id
    id = keySequence.incrementAndGet();
    created.add(id);
    return encode(id);
  }

  /**
   * Takes several existing ids as candidates and checks whether one of matches the given simple name.
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
      SimpleName sn = num.getSimple(key);
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

  static int decode(String id) {
    try {
      return IdConverter.LATIN32.decode(id);
    } catch (IllegalArgumentException e) {
      return -1;
    }
  }

  static String encode(int id) {
    return IdConverter.LATIN32.encode(id);
  }

}
