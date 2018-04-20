package org.col.api.vocab;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue {
  /**
   * The scientific name string could not be parsed at all, but appears to be a parsable name type,
   * i.e. it is not classified as a virus or hybrid formula.
   */
  UNPARSABLE_NAME,

  /**
   * The beginning of the scientific name string was parsed,
   * but there is additional information in the string that was not understood.
   */
  PARTIALLY_PARSABLE_NAME,

  /**
   * The authorship string could not be parsed.
   */
  UNPARSABLE_AUTHORSHIP,

  /**
   * The name has been classified as doubtful by the parser.
   */
  DOUBTFUL_NAME,

  /**
   * Authorship found in scientificName and scientificNameAuthorship differ.
   */
  INCONSISTENT_AUTHORSHIP,

  /**
   * An parsed, but inconsistent name.
   * E.g. the rank of the name does not match the given name parts or suffices.
   */
  INCONSISTENT_NAME,

  /**
   * The parsed scientific name string results in a different name
   * then was given as already atomized parts.
   */
  PARSED_NAME_DIFFERS,

  /**
   * The name parts contain unusual characters.
   */
  UNUSUAL_CHARACTERS,

  /**
   * At least one epithet equals literal value "null" or "none".
   */
  NULL_EPITHET,

  /**
   * Name was considered species but contains infraspecific epithet
   */
  SUBSPECIES_ASSIGNED,

  /**
   * lower case monomial match
   */
  LC_MONOMIAL,

  /**
   * indetermined cultivar without cultivar epithet
   */
  INDET_CULTIVAR,

  /**
   * indetermined species without specific epithet
   */
  INDET_SPECIES,

  /**
   * indetermined infraspecies without infraspecific epithet
   */
  INDET_INFRASPECIES,

  /**
   * binomial with rank higher than species aggregate
   */
  HIGHER_RANK_BINOMIAL,

  /**
   * question marks removed
   */
  QUESTION_MARKS_REMOVED,

  /**
   * removed enclosing quotes
   */
  REPL_ENCLOSING_QUOTE,

  /**
   * epithet without genus
   */
  MISSING_GENUS,

  /**
   * Escaped characters such as html entities have been unescaped during interpretation.
   * The following escaped character formats are recognized:
   * 1) named, decimal or hexadecimal xml & html entities
   * 2) unicode entities U+0026
   * 3) hexadecimal or octal java unicode entites
   * 4) CSS & ECMA Javascript entities
   */
  ESCAPED_CHARACTERS,

  /**
   * dwc:nomenclaturalStatus could not be interpreted
   */
  NOMENCLATURAL_STATUS_INVALID,

  /**
   * dwc:nomenclaturalCode could not be interpreted
   */
  NOMENCLATURAL_CODE_INVALID,

  /**
   * A recombination with a basionym authorship which does not match the authorship of the linked basionym.
   */
  BASIONYM_AUTHOR_MISMATCH,

  /**
   * Record has a verbatim original name (basionym) which is not unique and refers to several records.
   */
  BASIONYM_NOT_UNIQUE,

  /**
   * Record has a original name (basionym) relationship which was derived from name & authorship comparison, but did not exist explicitly in the data.
   * This should only be flagged in programmatically generated GBIF backbone usages.
   */
  BASIONYM_DERIVED,

  /**
   * There have been more than one accepted name in a homotypical basionym group of names.
   * GBIF backbone specific issue.
   */
  CONFLICTING_BASIONYM_COMBINATION,

  /**
   * A basionym that claims to have itself another basionym.
   */
  CHAINED_BASIONYM,

  /**
   * A potential orthographic variant exists in the dataset.
   */
  POTENTIAL_ORTHOGRAPHIC_VARIANT,

  /**
   * A canonical homonym exists for this name in the dataset.
   */
  HOMONYM,

  /**
   * A bi/trinomial name published earlier than the parent genus was published.
   * This might indicate that the name should rather be a recombination.
   */
  PUBLISHED_BEFORE_GENUS,

  /**
   * Identifier for a reference could not be resolved.
   */
  REFERENCE_ID_INVALID,

  /**
   * A taxon, name or reference with duplicate ids.
   * We remove the subsequent records but flag the first record with this issue.
   */
  ID_NOT_UNIQUE,

  // TODO: TAXON ISSUES TO BE REVISED !!!


  /**
   * The value for dwc:parentNameUsageID could not be resolved.
   */
  PARENT_ID_INVALID,

  /**
   * The value for dwc:acceptedNameUsageID could not be resolved.
   */
  ACCEPTED_ID_INVALID,

  /**
   * The value for dwc:originalNameUsageID could not be resolved.
   */
  BASIONYM_ID_INVALID,

  /**
   * Synonym lacking an accepted name.
   */
  ACCEPTED_NAME_MISSING,

  /**
   * dwc:taxonRank could not be interpreted
   */
  RANK_INVALID,

  /**
   * dwc:taxonomicStatus could not be interpreted
   */
  TAXONOMIC_STATUS_INVALID,

  /**
   * acef:lifezone contains values that cannot be interpreted
   */
  LIFEZONE_INVALID,

  /**
   * acef:IsFossil or acef:HasPreHolocene contains values that cannot be interpreted
   */
  IS_FOSSIL_INVALID,

  /**
   * acef:IsRecent or acef:HasModern contains values that cannot be interpreted
   */
  IS_RECENT_INVALID,

  /**
   * dc:references, acef:InfraSpeciesURL or acef:SpeciesURL cannot be interpreted into a valid URL
   */
  URL_INVALID,

  /**
   * acef:LTSDate cannot be interpreted into a date
   */
  ACCORDING_TO_DATE_INVALID,
  
  /**
   * Date string was provided a year, but month and/or day were absent
   */
  PARTIAL_DATE,

  /**
   * If a synonym points to another synonym as its accepted taxon the chain is resolved.
   */
  CHAINED_SYNOYM,


  TAXONOMIC_STATUS_MISMATCH,

  /**
   * The child parent classification resulted into a cycle that needed to be resolved/cut.
   */
  PARENT_CYCLE,

  /**
   * The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks.
   */
  CLASSIFICATION_RANK_ORDER_INVALID,

  /**
   * The denormalized classification could not be applied to the name usage.
   * For example if the id based classification has no ranks.
   */
  CLASSIFICATION_NOT_APPLIED,

  /**
   * At least one vernacular name extension record attached to this name usage is empty or clearly not a name.
   */
  VERNACULAR_NAME_INVALID,

  /**
   * Transliterated vernacular name missing and generated automatically.
   */
  VERNACULAR_NAME_TRANSLITERATED,
  /**
   * At least one description extension record attached to this name usage is invalid.
   */
  DESCRIPTION_INVALID,

  /**
   * At least one distribution extension record attached to this name usage is invalid.
   */
  DISTRIBUTION_INVALID,

  DISTRIBUTION_AREA_INVALID,
  DISTRIBUTION_COUNTRY_INVALID,
  DISTRIBUTION_STATUS_INVALID,
  DISTRIBUTION_GAZETEER_INVALID,

  /**
   * At least one species profile extension record attached to this name usage is invalid.
   */
  SPECIES_PROFILE_INVALID,

  /**
   * At least one multimedia extension record attached to this name usage is invalid.
   * This covers multimedia coming in through various extensions including
   * Audubon core, Simple images or multimedia or EOL media.
   */
  MULTIMEDIA_INVALID,
  
  /**
   * The title extracted from the bibliographic citation does not match the directly provided title.
   */
  CSL_TITLE_MISMATCH,
  
  /**
   * The year extracted from the bibliographic citation does not match the directly provided year.
   */
  CSL_YEAR_MISMATCH,

  /**
   * The reference could not be parsed and broken down into a sensible record.
   */
  REFERENCE_UNPARSABLE,

  /**
   * At least one alternative identifier extension record attached to this name usage is invalid.
   */
  ALT_IDENTIFIER_INVALID,

  /**
   * Name usage could not be matched to the GBIF backbone.
   */
  BACKBONE_MATCH_NONE,

  /**
   * Name usage could only be matched to the GBIF backbone using fuzzy matching.
   *
   * @deprecated because there should be no fuzzy matching being used anymore for matching checklist names
   */
  @Deprecated
  BACKBONE_MATCH_FUZZY,

  /**
   * A scientific name has been used to point to another record (synonym->accepted, combination->basionym) which is not unique and refers to several records.
   */
  NAME_NOT_UNIQUE,

  /**
   * Record has a verbatim parent name which is not unique and refers to several records.
   */
  PARENT_NAME_NOT_UNIQUE,

  /**
   * There were problems representing all name usage relationships,
   * i.e. the link to the parent, accepted and/or original name.
   * The interpreted record in ChecklistBank is lacking some of the original source relation.
   */
  RELATIONSHIP_MISSING,

  /**
   * The group (currently only genera are tested) are lacking any accepted species
   * GBIF backbone specific issue.
   */
  NO_SPECIES,

  /**
   * The (accepted) bi/trinomial name does not match the parent name and should be recombined into the parent genus/species.
   * For example the species Picea alba with a parent genus Abies is a mismatch and should be replaced by Abies alba.
   * GBIF backbone specific issue.
   */
  NAME_PARENT_MISMATCH,

  /**
   * A potential orthographic variant exists in the backbone.
   * GBIF backbone specific issue.
   */
  ORTHOGRAPHIC_VARIANT;

}
