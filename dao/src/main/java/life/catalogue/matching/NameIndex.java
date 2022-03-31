package life.catalogue.matching;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.common.Managed;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NameIndex definition for different implementations.
 */
public interface NameIndex extends Managed, AutoCloseable {
  
  Logger LOG = LoggerFactory.getLogger(NameIndex.class);
  
  /**
   * Tries to match a parsed name against the names index.
   *
   * @param name         the parsed name to match against, ignoring any ids if present
   * @param allowInserts if true inserts the name to be matched into the index if not yet existing, avoiding NoMatch responses
   * @param verbose      if true adds verbose matching information, i.e. queue of alternative matches
   * @return a match which is never null, but might have a usageKey=null if nothing could be matched
   */
  NameMatch match(Name name, boolean allowInserts, boolean verbose);

  /**
   * Lookup IndexName by its key
   */
  IndexName get(Integer key);

  /**
   * List all index names for a given canonical name key, but not the canonical name itself!
   */
  Collection<IndexName> byCanonical(Integer key);

  Iterable<IndexName> all();

  /**
   * @return the number of names in the index
   */
  int size();
  
  /**
   * Adds a new name to the index, generating a new key and potentially inserting a canonical name record too.
   * It will add a new IndexName even if it exists already.
   * In most cases the {@link #match(Name, boolean, boolean)}match method should be the preferred way to include only new names
   *
   * @param name
   */
  void add(IndexName name);
  
  /**
   * Adds a batch of names to the index, see {@link #add(IndexName)} for details.
   */
  default void addAll(Collection<IndexName> names) {
    LOG.info("Adding {} names", names.size());
    for (IndexName n : names) {
      add(n);
    }
  }

  /**
   * Resets the names index, removing all entries and setting back the id sequence to 1.
   * This does truncate both the file based index as well as the underlying postgres data.
   */
  void reset();

  /**
   * @return true if started and ready to be queried
   */
  boolean hasStarted();

  /**
   * Makes sure the names index has started and throws an NamesIndexOfflineException otherwise
   */
  default NameIndex assertOnline() {
    if (!hasStarted()) {
      throw UnavailableException.unavailable("Names Index");
    }
    return this;
  }

  @Override
  default void close() throws Exception {
    stop();
  }

}
