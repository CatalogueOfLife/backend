package org.col.matching;

import org.col.api.model.Name;

/**
 *
 */
public interface NameMatching {

  /**
   * Tries to match a parsed name against the names index.
   *
   * @param name  the parsed name to match against, ignoring any ids optionally present
   * @param strict          if true only tries to match scrictly the scientific name, if false (the default) the matching
   *                        service tries also to match the lowest possible taxon from the given classification
   * @param verbose         if true adds verbose matching information, i.e. list of alternative matches
   *
   * @return a match which is never null, but might have a usageKey=null if nothing could be matched
   */
  NameMatch match(Name name, boolean strict, boolean verbose);
}
