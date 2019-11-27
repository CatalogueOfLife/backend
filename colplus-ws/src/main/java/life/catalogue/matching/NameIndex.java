package life.catalogue.matching;

import java.util.Collection;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface NameIndex extends AutoCloseable {
  
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
   * @return the number of names in the index
   */
  int size();
  
  /**
   * Adds a name to the index
   *
   * @param name
   */
  void add(Name name);
  
  /**
   * Adds a batch of names to the index
   */
  default void addAll(Collection<Name> names) {
    LOG.info("Adding {} names", names.size());
    for (Name n : names) {
      add(n);
    }
  }
  
  default void close() throws Exception {
    // nothing
  }
}
