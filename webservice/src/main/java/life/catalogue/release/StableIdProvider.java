package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.*;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.text.StringUtils;
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
 * Basic steps:
 *
 * 1) Generate a ReleaseView (use interface to allow for different impls) on all previous releases,
 *    keyed on their usage id and names index id (nxId).
 *    For each id only use the version from its latest release.
 *    Include ALL ids, also deleted ones.
 *    Convert ids to their int representation to save memory and simplify comparison etc.
 *    Expose only properties needed for matching, i.e. id (int), nxId (int), status, parentID (int), ???
 *
 * 2) Match all usages, ordered for matching by their status, then rank from top down (allows to compare parentIds):
 *    First assign ids for accepted, then prov accepted, synonyms, ambiguous syns and finally misapplied names
 *    -
 * 3) Use new "usage" matching service that sits on top of the ReleaseView
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

  private final AtomicInteger keySequence = new AtomicInteger();
  // names index ids to list of usage ids (converted to ints) of previous release
  private Int2ObjectMap<IntArrayList> prevIds; // just the ids that are not part of the project already!!!
  private Int2ObjectMap<IntArrayList> prevDel; // ids that existed in any previous release, but was since deleted and not part of the previous release
  private Int2IntMap id2dataset = new Int2IntOpenHashMap(); // maps existing ids to their last used release datasetKey
  // reverse map of ID to names index ids, but to save memory only for the few IDs that have more than one index id!
  private Int2ObjectMap<IntArrayList> id2Nidx;
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
    // TODO: load prev releases via NameUsageIndexIds
    // just the ids that are not already part of the project!!!
    prevIds = new Int2ObjectOpenHashMap<>();
    prevDel = new Int2ObjectOpenHashMap<>();
    // keep reverse map of ID to names index ids, but to save memory only for the few IDs that have more than one index id!
    id2Nidx = new Int2ObjectOpenHashMap<>();
    for (Int2ObjectMap.Entry<IntArrayList> x : prevIds.int2ObjectEntrySet()) {
    }
    // TODO: populate id2dataset
    id2dataset.clear();
    keySequence.set(findMaxKey());

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


  /**
   * Retrieves the highest key ever assigned to a name usage in any release of the project.
   * Keys are encoded as short string in the db, so we cannot just do a select max() in SQL.
   * But the higher the key, the longer the string, so we can preselect without decoding all of them.
   * @return highest key, decoded as an int
   */
  private int findMaxKey() {
    String maxID = null;
    // max has the longest id and ids sort alphanumerically
    int maxLen = -1;
    // check non temp ids from curr project
    try (SqlSession session = factory.openSession(false)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (String id : num.processIds(datasetKey)) {
        if (maxLen < id.length()) {
          maxLen = id.length();
          maxID = id;
        } else if (maxLen == id.length()) {
          if (maxID.compareTo(id) > 0) {
            maxID = id;
          }
        }
      }
    }
    int max = maxID == null ? 0 : decode(maxID);
    // now also check all ids from the previous (curr & deleted)
    max = findMax(max, prevIds);
    max = findMax(max, prevDel);
    return max;
  }

  private static int findMax(int max, Int2ObjectMap<IntArrayList> idMap) {
    for (IntArrayList ids : idMap.values()) {
      for (int id : ids) {
        if (id > max) {
          max = id;
        };
      }
    }
    return max;
  }
}
