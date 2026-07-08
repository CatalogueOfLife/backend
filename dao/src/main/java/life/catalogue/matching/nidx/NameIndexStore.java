package life.catalogue.matching.nidx;

import life.catalogue.api.model.IndexName;
import life.catalogue.common.Managed;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Function;

public interface NameIndexStore extends Managed {

  /**
   * Lookup IndexName by its key
   */
  IndexName get(Integer key);

  /**
   * The names index is single-tier & canonical-only: every entry is its own canonical name, so
   * there are no separate rank/author specific child entries grouped underneath it anymore.
   * This therefore always returns an empty collection - it is kept for API compatibility only.
   * @param key the canonical name index key
   */
  Collection<IndexName> byCanonical(Integer key);

  Iterable<IndexName> all();

  /**
   * The maximum key of all stored names index entries.
   * @return max key or zero if store is empty
   */
  int maxKey();

  /**
   * Counts all name usages. Potentially an expensive operation.
   */
  int count();

  /**
   * Remove all entries of the names index store
   */
  void clear();

  /**
   * Deletes the names index entry.
   * If it is a canonical one, it will also remove all qualified entries based on it.
   */
  List<IndexName> delete(int id, Function<IndexName, String> keyFunc);

  List<IndexName> get(String key);
  
  boolean containsKey(String key);
  
  void add(String key, IndexName name);

  /**
   * Tries to compact the store, but retaining all identifiers.
   */
  void compact();

  /**
   * DateTime the store was first created or entirely cleared.
   */
  LocalDateTime created();

  Pool<Kryo> kryo();
}
