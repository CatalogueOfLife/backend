package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.id.IdConverter;

import org.gbif.nameparser.api.Rank;

import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Tracks released ids incl historic releases.
 * Each ID is only represented by the first/earliest, i.e. the lowest release attempt.
 * As release attempts are unique and sequential for all releases of the project
 * we do not care if the ID first originated from a public Release or XRelease.
 */
public class ReleasedIds {

  private int maxKey = 0;
  private final Int2ObjectMap<ReleasedId> byId = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<ReleasedId[]> byCanonId = new Int2ObjectOpenHashMap<>();

  public static class ReleasedId {
    public final int id;
    public final int nxId;
    public final int canonId;
    public final int attempt;
    public final boolean isCurrent;
    public final MatchType matchType;
    public final Rank rank;
    public final String authorship;
    public final String phrase;
    public final TaxonomicStatus status;
    public final String parent; // this should be the scientific name of the parent, not the ID !!!
    public final TaxGroup group;

    /**
     * @param sn simple name with parent being a scientificName, not ID!
     * @throws IllegalArgumentException if the string id cannot be converted into an int, e.g. if it was a temp UUID
     */
    public static ReleasedId create(SimpleNameWithNidx sn, int attempt, boolean currentID) throws IllegalArgumentException {
      return new ReleasedId(IdConverter.LATIN29.decode(sn.getId()), attempt, currentID, sn);
    }

    /**
     * @param sn simple name with parent being a scientificName, not ID!
     */
    protected ReleasedId(int id, int attempt, boolean isCurrent, SimpleNameWithNidx sn) {
      this.id = id;
      this.nxId = sn.getNamesIndexId();
      this.canonId = sn.getCanonicalId();
      this.attempt = attempt;
      this.isCurrent = isCurrent;
      this.matchType = sn.getNamesIndexMatchType();
      this.rank = sn.getRank();
      this.authorship = sn.getAuthorship();
      this.phrase = sn.getPhrase();
      this.status = sn.getStatus();
      this.parent = sn.getParent();
      this.group = sn.getGroup();
    }

    public String id() {
      return IdConverter.LATIN29.encode(id);
    }

    public boolean isCanonical() {
      return Objects.equals(canonId, nxId);
    }
  }

  public int size() {
    return byId.size();
  }

  public boolean isEmpty() {
    return byId.isEmpty();
  }

  public boolean containsId(int i) {
    return byId.containsKey(i);
  }

  public void log() {
    System.out.println(
      byId.keySet().stream().sorted().map(String::valueOf).collect(Collectors.joining(" "))
    );
  }

  public ReleasedId remove(int id) throws IllegalArgumentException {
    ReleasedId r = byId.remove(id);
    if (r != null) {
      ReleasedId[] rids = ArrayUtils.removeAllOccurrences(byCanonId.get(r.canonId), r);
      if (rids == null || rids.length == 0) {
        byCanonId.remove(r.canonId);
      } else {
        byCanonId.put(r.canonId, rids);
      }
    }
    return r;
  }

  /**
   * @return (remaining) IDs which are flagged as current
   */
  Int2IntMap currentIDs(){
    Int2IntMap ids = new Int2IntOpenHashMap();
    for (ReleasedId rid : byId.values()) {
      if (rid.isCurrent) {
        ids.put(rid.id, rid.attempt);
      }
    }
    return ids;
  }

  /**
   * @return number of IDs from the last (=max) attempt.
   */
  public int currentIdCount(){
    int counter = 0;
    for (ReleasedId rid : byId.values()) {
      if (rid.isCurrent) {
        counter++;
      }
    }
    return counter;
  }

  void add (ReleasedId id) {
    if (byId.containsKey(id.id)) {
      // duplicate ids should not get here. throw
      throw new IllegalStateException("Duplicate identifier. ReleaseId "+ id.attempt + ":" + id.id +" already exists in attempt " + byId.get(id.id).attempt);
    }
    byId.put(id.id, id);
    if (byCanonId.containsKey(id.canonId)) {
      byCanonId.put(id.canonId, ArrayUtils.add(byCanonId.get(id.canonId), id));
    } else {
      byCanonId.put(id.canonId, new ReleasedId[]{id});
    }
    considerMaxID(id);
  }

  void considerMaxID(ReleasedId id) {
    if (id.id > maxKey) {
      maxKey = id.id;
    }
  }

  public ReleasedId[] byCanonId(int canonId) {
    return byCanonId.getOrDefault(canonId, null);
  }

  public ReleasedId byId(int id) {
    return byId.get(id);
  }

  public boolean hasId(int id) {
    return byId.containsKey(id);
  }

  public int maxKey(){
    return maxKey;
  }

}
