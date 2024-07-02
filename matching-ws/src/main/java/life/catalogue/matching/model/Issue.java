package life.catalogue.matching.model;

public enum Issue {

  /**
   * Indicates one of the following:
   * The …ID was not used when mapping the record to the checklist. This may indicate one of:
   * The ID uses a pattern not configured for use with the checklist
   * The ID did not uniquely(!) identify a concept in the checklist
   * The ID found a concept in the checklist that did not map to the backbone
   * A different ID was used, or the record names were used, as no ID lookup successfully linked to the backbone
   */
  TAXON_MATCH_SCIENTIFIC_NAME_ID_IGNORED,
  TAXON_MATCH_TAXON_CONCEPT_ID_IGNORED,
  TAXON_MATCH_TAXON_ID_IGNORED,

  /**
   * Indicates one of the following:
   * The …ID matched a known pattern, but it was not found in the associated checklist.
   * The backbone lookup was performed using either the names or a different ID field from the record.
   * This may indicate a poorly formatted identifier or may be caused by a newly created ID that
   * isn't yet known in the version of the published checklist.
   */
  SCIENTIFIC_NAME_ID_NOT_FOUND,
  TAXON_CONCEPT_ID_NOT_FOUND,
  TAXON_ID_NOT_FOUND,

  /**
   * The scientificName provided in the occurrence record does not precisely match the name in the registered checklist when
   * using the scientificNameID, taxonID or taxonConceptID to look it up. Publishers are advised to check the IDs are correct,
   * or update the formatting of the names on their records.
   */
  SCIENTIFIC_NAME_AND_ID_INCONSISTENT,
  /**
   * A name usage was found using the scientificNameID, taxonID or taxonConceptID, but it differs from what
   * would have been found if the classification names on the record were used. This may indicate a gap in the
   * checklist, a poor mapping between the checklist and the backbone, or a mismatch between the classification
   * names and the declared IDs (scientificNameID or taxonConceptID) on the occurrence record itself.
   */
  TAXON_MATCH_NAME_AND_ID_AMBIGUOUS
}
