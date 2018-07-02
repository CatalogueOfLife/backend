package org.col.admin.matching;

import org.col.api.model.Name;
import org.col.api.model.NameMatch;

public interface NameIndex extends AutoCloseable {

  /**
   * Tries to match a parsed name against the names index.
   *
   * @param name  the parsed name to match against, ignoring any ids if present
   * @param allowInserts   if true inserts the name to be matched into the index if not yet existing, avoiding NoMatch responses
   * @param verbose        if true adds verbose matching information, i.e. list of alternative matches
   *
   * @return a match which is never null, but might have a usageKey=null if nothing could be matched
   */
  NameMatch match(Name name, boolean allowInserts, boolean verbose);

  /**
   * @return the number of names in the index
   */
  int size();

  /**
   * Adds a name to the index
   * @param name
   */
  void add(Name name);

  /**
   * Adds a batch of names to the index
   */
  default void addAll(Iterable<Name> names) {
    for (Name n : names) {
      add(n);
    }
  }

  default void close() throws Exception {
    // nothing
  }
}
