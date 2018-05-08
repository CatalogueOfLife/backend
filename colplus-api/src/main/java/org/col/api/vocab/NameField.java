package org.col.api.vocab;

/**
 * Explicit list of available Name fields that can be searched on.
 * The enum provides the database field name for the property to be used by mybatis.
 *
 * Remember to update this enum when the db name table or Name class changes!
 */
public enum NameField {
  UNINOMIAL,
  GENUS,
  INFRAGENERIC_EPITHET,
  SPECIFIC_EPITHET,
  INFRASPECIFIC_EPITHET,
  CULTIVAR_EPITHET,
  STRAIN,
  CANDIDATUS,
  NOTHO,
  BASIONYM_AUTHORS,
  BASIONYM_EX_AUTHORS,
  BASIONYM_YEAR,
  COMBINATION_AUTHORS,
  COMBINATION_EX_AUTHORS,
  COMBINATION_YEAR,
  SANCTIONING_AUTHOR,
  NOM_STATUS,
  PUBLISHED_IN_KEY,
  PUBLISHED_IN_PAGE,
  SOURCE_URL,
  REMARKS;

  public String sql() {
    return this.name().toLowerCase();
  }
}
