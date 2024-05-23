package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.id.IdConverter;

import org.gbif.nameparser.api.Rank;

import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * Tracks released ids incl historic releases.
 * Each ID is only represented by the most recent, i.e. usually the highest release attempt.
 * As we mix identifiers from Release and XRelease there might be a more recent public release
 * than the last one of the kind we are trying to release.
 *
 * For example we can do an extended release with based on a regular release which is newer than the last extended release.
 * As release attempts are unique and sequential for all releases of the project there can be identifiers from the last regular release
 * which have a higher attempt than the last XRelease.
 */
public class ReleasedIds {

  private int maxKey = 0;
  private int maxAttempt = 0;
  private final int lastAttempt; // attempt of last release of same origin
  private final Int2ObjectMap<ReleasedId> byId = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<ReleasedId[]> byNxId = new Int2ObjectOpenHashMap<>();

  public ReleasedIds(Integer lastAttempt) {
    this.lastAttempt = lastAttempt == null ? -999 : lastAttempt;
  }

  public static class ReleasedId {
    public final int id;
    public final int nxId;
    public final int attempt;
    public final boolean sameOrigin;
    public final MatchType matchType;
    public final Rank rank;
    public final String authorship;
    public final String phrase;
    public final TaxonomicStatus status;
    public final String parent;

    /**
     * @throws IllegalArgumentException if the string id cannot be converted into an int, e.g. if it was a temp UUID
     */
    public static ReleasedId create(SimpleNameWithNidx sn, int attempt, boolean sameOrigin) throws IllegalArgumentException {
      return new ReleasedId(IdConverter.LATIN29.decode(sn.getId()), attempt, sameOrigin, sn);
    }

    public ReleasedId(int id, int attempt, boolean sameOrigin, SimpleNameWithNidx sn) {
      this.id = id;
      this.nxId = sn.getNamesIndexId();
      this.attempt = attempt;
      this.sameOrigin = sameOrigin;
      this.matchType = sn.getNamesIndexMatchType();
      this.rank = sn.getRank();
      this.authorship = sn.getAuthorship();
      this.phrase = sn.getPhrase();
      this.status = sn.getStatus();
      this.parent = sn.getParent();
    }

    public String id() {
      return IdConverter.LATIN29.encode(id);
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
      ReleasedId[] rids = ArrayUtils.removeAllOccurences(byNxId.get(r.nxId), r);
      if (rids == null || rids.length == 0) {
        byNxId.remove(r.nxId);
      } else {
        byNxId.put(r.nxId, rids);
      }
    }
    return r;
  }

  /**
   * @return IDs from the last (=max) attempt.
   */
  public Int2IntMap lastAttemptIds(){
    Int2IntMap ids = new Int2IntOpenHashMap();
    for (ReleasedId rid : byId.values()) {
      if (rid.attempt == lastAttempt) {
        ids.put(rid.id, rid.attempt);
      }
    }
    return ids;
  }

  /**
   * @return number of IDs from the last (=max) attempt.
   */
  public int lastAttemptIdCount(){
    int counter = 0;
    for (ReleasedId rid : byId.values()) {
      if (rid.attempt == lastAttempt) {
        counter++;
      }
    }
    return counter;
  }

  public int getLastAttempt() {
    return lastAttempt;
  }

  void add (ReleasedId id) {
    if (byId.containsKey(id.id)) {
      // ignore already existing ids, but make sure the existing attempt is more recent if it is from the same origin, i.e. higher!
      if (id.sameOrigin && byId.get(id.id).attempt < id.attempt) {
        throw new IllegalStateException("Cannot add a newer ReleaseId "+ id.attempt + ":" + id.id +" than existing attempt " + byId.get(id.id).attempt);
      }
      return;
    }
    byId.put(id.id, id);
    if (byNxId.containsKey(id.nxId)) {
      byNxId.put(id.nxId, ArrayUtils.add(byNxId.get(id.nxId), id));
    } else {
      byNxId.put(id.nxId, new ReleasedId[]{id});
    }
    if (id.id > maxKey) {
      maxKey = id.id;
    }
    if (id.attempt > maxAttempt) {
      maxAttempt = id.attempt;
    }

  }

  public ReleasedId[] byNxId(int nxId) {
    return byNxId.getOrDefault(nxId, null);
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

  /**
   * @return the maximum attempt in this list of all releases, i.e. the last or current attempt
   */
  public int getMaxAttempt() {
    return maxAttempt;
  }
}
