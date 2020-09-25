package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import life.catalogue.common.id.IdConverter;
import org.apache.commons.lang3.ArrayUtils;

public class ReleasedIds {

  private int maxKey = 0;
  private final Int2ObjectMap<ReleasedId> byId = new Int2ObjectOpenHashMap();
  private final Int2ObjectMap<ReleasedId[]> byNxId = new Int2ObjectOpenHashMap();

  public static class ReleasedId {
    public final int id;

    public ReleasedId(int id, int[] nxId, int attempt) {
      this.id = id;
      this.nxId = nxId;
      this.attempt = attempt;
    }

    public final int[] nxId;
    public final int attempt;

    public String id() {
      return IdConverter.LATIN32.encode(id);
    }
  }

  public int size() {
    return byId.size();
  }

  public void remove(int id) {
    ReleasedId r = byId.remove(id);
    if (r != null) {
      for (int nx : r.nxId) {
        ReleasedId[] rids = ArrayUtils.removeAllOccurences(byNxId.get(nx), r);
        if (rids == null || rids.length == 0) {
          byNxId.remove(nx);
        } else {
          byNxId.put(nx, rids);
        }
      }
    }
  }

  void add (ReleasedId id) {
    byId.put(id.id, id);
    for (int nx : id.nxId) {
      if (byNxId.containsKey(nx)) {
        byNxId.put(nx, ArrayUtils.add(byNxId.get(nx), id));
      } else {
        byNxId.put(nx, new ReleasedId[]{id});
      }
    }
    if (id.id > maxKey) {
      maxKey = id.id;
    }
  }

  public ReleasedId[] byNxId(int nxId) {
    return byNxId.getOrDefault(nxId, null);
  }

  public ReleasedId byId(int id) {
    return byId.get(id);
  }

  public int maxKey(){
    return maxKey;
  }
}
