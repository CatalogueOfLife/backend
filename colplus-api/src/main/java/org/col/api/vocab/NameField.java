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
  BASIONYM_AUTHORS(true),
  BASIONYM_EX_AUTHORS(true),
  BASIONYM_YEAR,
  COMBINATION_AUTHORS(true),
  COMBINATION_EX_AUTHORS(true),
  COMBINATION_YEAR,
  SANCTIONING_AUTHOR,
  NOM_STATUS,
  PUBLISHED_IN_ID,
  PUBLISHED_IN_PAGE,
  SOURCE_URL,
  REMARKS;

  private boolean array;

  NameField() {
    this(false);
  }

  NameField(boolean array) {
    this.array = array;
  }

  public String notNull(String alias) {
    if (array) {
      return "array_length(" + alias + '.' + this.name().toLowerCase() + ", 1) IS NOT NULL";
    } else {
      return alias + '.' + this.name().toLowerCase() + " IS NOT NULL";
    }
  }
}
