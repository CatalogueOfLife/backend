package life.catalogue.api.vocab;

/**
 * Explicit list of available Name(Usage) fields that can be searched on.
 * <p>
 * Remember to update this enum when the Name(Usage) class changes!
 */
public enum NameField {
  // Name fields
  UNINOMIAL,
  GENUS,
  INFRAGENERIC_EPITHET,
  SPECIFIC_EPITHET,
  INFRASPECIFIC_EPITHET,
  CULTIVAR_EPITHET,
  CANDIDATUS,
  NOTHO,
  BASIONYM_AUTHORS,
  BASIONYM_EX_AUTHORS,
  BASIONYM_YEAR,
  COMBINATION_AUTHORS,
  COMBINATION_EX_AUTHORS,
  COMBINATION_YEAR,
  SANCTIONING_AUTHOR,
  CODE,
  NOM_STATUS,
  PUBLISHED_IN,
  PUBLISHED_IN_PAGE,
  NOMENCLATURAL_NOTE,
  UNPARSED,
  REMARKS,
  // NameUsage fields
  NAME_PHRASE,
  ACCORDING_TO;

}
