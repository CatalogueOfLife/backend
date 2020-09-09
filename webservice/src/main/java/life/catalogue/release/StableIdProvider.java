package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.*;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.text.StringUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.IdMapMapper;
import life.catalogue.db.mapper.NameUsageMapper;
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
  private final AtomicInteger keySequence = new AtomicInteger();
  // id changes in this release
  private IntSet added = new IntOpenHashSet();
  private IntSet deleted = new IntOpenHashSet();
  private IntSet resurrected = new IntOpenHashSet();
  private NameUsageMapper num;

  public StableIdProvider(int datasetKey, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.factory = factory;
  }

  public void run() {
    LOG.info("Map name usage IDs");
    prepare();
    verifyExistingIds();
    replaceTemporaryIds();
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
        int attempt = rel.getImportAttempt();
        attempt2dataset.put(attempt, (int)rel.getKey());
        session.getMapper(NameUsageMapper.class).processNxIds(rel.getKey()).forEach(sn -> {
          ids.add(new ReleasedIds.ReleasedId(decode(sn.getId()), sn.getNameIndexIds().toIntArray(), attempt));
        });
      }
    }
    keySequence.set(ids.maxKey());
  }

  private void verifyExistingIds(){

  }

  private void replaceTemporaryIds(){
    // we keep a list of usages that have ambiguous matches to multiple index names
    // and deal with those names last.
    AtomicInteger counter = new AtomicInteger();
    List<SimpleNameWithNidx> ambiguous = new ArrayList<>();
    DSID<String> key = DSID.of(datasetKey, "");
    try (SqlSession session = factory.openSession(false)) {
      num = session.getMapper(NameUsageMapper.class);
      num.processTemporary(datasetKey).forEach(u -> {
        if (u.getNameIndexIds().isEmpty()) {
          LOG.debug("Usage with no name match id - keep the temporary id");
        } else if (u.getNameIndexIds().size() == 1) {
          String id = issueID(u, u.getNameIndexIds().toIntArray());
          num.updateId(key.id(u.getId()), id);
          if (counter.incrementAndGet() % 1000 == 0) {
            session.commit();
          }
        } else {
          ambiguous.add(u);
        }
      });
      session.commit();

      LOG.info("Updated {} temporary ids for simple matches, doing {} ambiguous matches next", counter, ambiguous.size());
      for (SimpleNameWithNidx u : ambiguous){
        String id = issueID(u, u.getNameIndexIds().toIntArray());
        num.updateId(key.id(u.getId()), id);
        if (counter.incrementAndGet() % 1000 == 0) {
          session.commit();
        }
      }
      session.commit();
    }
  }

  private String issueID(SimpleName name, int... nidxs) {
    int id;
    // does a previous id match?
    for (int nidx : nidxs) {
      if (prevIds.containsKey(nidx)) {
        id = selectID(prevIds.get(nidx), name);
        if (id > 0) {
          removeID(id, nidx);
          return encode(id);
        }
      }
    }
    // if not, does a previously deleted id match?
    for (int nidx : nidxs) {
      if (prevDel.containsKey(nidx)) {
        id = selectID(prevDel.get(nidx), name);
        if (id > 0) {
          removeID(id, nidx);
          resurrected.add(id);
          return encode(id);
        }
      }
    }
    // if not, issue a new id
    id = keySequence.incrementAndGet();
    added.add(id);
    return encode(id);
  }

  /**
   * Removes an CoL ID from the previous maps so its not issued again in this release
   */
  private void removeID(int id, int nidx){
    _removeID(id, nidx);
    // do we have more index ids associated with the same id that we need to remove?
    if (id2Nidx.containsKey(id)) {
      for (int nidx2 : id2Nidx.get(id)){
        if (nidx == nidx2) continue;
        _removeID(id, nidx2);
      }
      id2Nidx.remove(id);
    }
  }
  private void _removeID(int id, int nidx){
    if (prevIds.containsKey(nidx)) {
      prevIds.get(nidx).removeInt(id);
    }
    if (prevDel.containsKey(nidx)) {
      prevDel.get(nidx).removeInt(id);
    }
  }

  /**
   * Takes several existing ids as candidates and checks whether one of matches the given simple name.
   * It is assumed all ids match the canonical name at least.
   * Selecting considers authorship and the parent name to select between multiple candidates.
   *
   * @param ids id candidates from any previous release (but not from the current project)
   * @param name the name to match against
   * @return the matched id or -1 for no match
   */
  private int selectID(IntCollection ids, SimpleName name) {
    if (ids.size() == 1) return ids.iterator().nextInt();
    // select best id from multiple
    int max = 0;
    IntSet best = new IntOpenHashSet();
    for (int id : ids) {
      DSID<String> key = DSID.of(id2dataset.get(id), encode(id));
      SimpleName sn = num.getSimple(key);
      int score = 0;
      // author 0-2
      if (name.getAuthorship() != null && Objects.equals(name.getAuthorship(), sn.getAuthorship())) {
        score += 2;
      } else if (StringUtils.equalsIgnoreCaseAndSpace(name.getAuthorship(), sn.getAuthorship())) {
        score += 1;
      }
      // parent 0-2
      if (name.getParent() != null && Objects.equals(name.getParent(), sn.getParent())) {
        score += 2;
      }
      if (score > max) {
        max = score;
        best.clear();
        best.add(id);
      } else if (score == max) {
        best.add(id);
      }
    }
    if (max > 0) {
      if (best.size() > 1) {
        LOG.debug("{} ids all matching to {}", Arrays.toString(best.toArray()), name);
      }
      return best.iterator().nextInt();
    }
    return -1;
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
