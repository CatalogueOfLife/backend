package org.col.matching;

public enum MatchType {
  /*
   * The canonical name matches exactly
   */
  EXACT,

  /*
   * The name and/or authorship matches an orthographic variant of the name
   * which is considered to be the same name still.
   */
  VARIANT,

  NONE
}
