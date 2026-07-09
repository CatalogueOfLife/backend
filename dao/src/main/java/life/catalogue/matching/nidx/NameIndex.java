package life.catalogue.matching.nidx;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.common.Managed;

import java.time.LocalDateTime;

import life.catalogue.parser.NameParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NameIndex definition for different implementations.
 */
public interface NameIndex extends Managed, AutoCloseable {

  Logger LOG = LoggerFactory.getLogger(NameIndex.class);

  LocalDateTime created();

  /**
   * Tries to match a parsed name against the names index.
   *
   * @param name         the parsed name to match against, ignoring any ids if present
   * @param allowInserts if true inserts the name to be matched into the index if not yet existing, avoiding NoMatch responses
   * @param verbose      if true adds verbose matching information (currently unused - the index no longer keeps alternatives)
   * @return a match which is never null, but might have nidx=null / matched=false if nothing could be matched
   */
  NameMatch match(Name name, boolean allowInserts, boolean verbose);

  /**
   * Parses and matches a simple name against the names index.
   */
  default NameMatch match(SimpleName sn, boolean allowInserts, boolean verbose) {
    var opt = NameParser.PARSER.parse(sn);
      if (opt.isPresent()) {
      var pnu = opt.get();
      return match(pnu.getName(), allowInserts, verbose);
    }
    return NameMatch.noMatch();
  }

  /**
   * Lookup the canonical IndexName carrier by its key.
   * Backed by the postgres names_index table (the slim store no longer holds IndexName instances).
   */
  IndexName get(Integer key);

  /**
   * The names index is single-tier & canonical-only: every entry is its own canonical name, so this
   * simply returns the given key if an entry exists for it, or null otherwise.
   */
  default Integer getCanonical(Integer key) {
    var ni = get(key);
    if (ni != null) {
      return ni.getKey();
    }
    return null;
  }

  /**
   * Prints basic index info to stdout for debugging purposes.
   */
  default void printIndex() {
    System.out.println("Names Index with " + size() + " names");
  }

  /**
   * @return the number of names in the index
   */
  int size();

  /**
   * Resets the names index, removing all entries and setting back the id sequence to 1.
   * This does truncate both the file based index as well as the underlying postgres data.
   */
  void reset();

  /**
   * Makes sure the names index has started and throws an UnavailableException otherwise
   */
  @Override
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

  NameIndexStore store();

}
