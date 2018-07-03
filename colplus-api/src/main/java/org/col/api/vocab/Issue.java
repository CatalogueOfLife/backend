package org.col.api.vocab;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue {

//
// GENERAL DATA ISSUES
//


  /**
   * A verbatim record could not be interpreted at all and has been ignored.
   */
  NOT_INTERPRETED(Group.DATA, Level.ERROR),

  /**
   * Escaped characters such as html entities have been unescaped during interpretation.
   * The following escaped character formats are recognized:
   * 1) named, decimal or hexadecimal xml & html entities
   * 2) unicode entities U+0026
   * 3) hexadecimal or octal java unicode entites
   * 4) CSS & ECMA Javascript entities
   */
  ESCAPED_CHARACTERS(Group.DATA),

  /**
   * Identifier for a reference could not be resolved.
   * This can be from any entity, e.g. name, distribution, vernaculars, etc
   */
  REFERENCE_ID_INVALID(Group.DATA, Level.ERROR),

  /**
   * A taxon, name or reference with duplicate ids.
   * We remove the subsequent records but flag the first record with this issue.
   */
  ID_NOT_UNIQUE(Group.DATA, Level.ERROR),

  /**
   * dc:references, acef:InfraSpeciesURL or acef:SpeciesURL cannot be interpreted into a valid URL
   */
  URL_INVALID(Group.DATA, Level.ERROR),

  /**
   * Date string was provided a year, but month and/or day were absent
   */
  PARTIAL_DATE(Group.DATA),


//
// NAME ISSUES
//

  /**
   * The scientific name string could not be parsed at all, but appears to be a parsable name type,
   * i.e. it is not classified as a virus or hybrid formula.
   */
  UNPARSABLE_NAME(Group.NAME),

  /**
   * The beginning of the scientific name string was parsed,
   * but there is additional information in the string that was not understood.
   */
  PARTIALLY_PARSABLE_NAME(Group.NAME),

  /**
   * The authorship string could not be parsed.
   */
  UNPARSABLE_AUTHORSHIP(Group.NAME),

  /**
   * The name has been classified as doubtful by the parser.
   */
  DOUBTFUL_NAME(Group.NAME),

  /**
   * Authorship found in scientificName and scientificNameAuthorship differ.
   */
  INCONSISTENT_AUTHORSHIP(Group.NAME),

  /**
   * An parsed, but inconsistent name.
   * E.g. the rank of the name does not match the given name parts or suffices.
   */
  INCONSISTENT_NAME(Group.NAME),

  /**
   * The parsed scientific name string results in a different name
   * then was given as already atomized parts.
   */
  PARSED_NAME_DIFFERS(Group.NAME),

  /**
   * The name parts contain unusual characters.
   */
  UNUSUAL_NAME_CHARACTERS(Group.NAME),

  /**
   * At least one epithet equals literal value "null" or "none".
   */
  NULL_EPITHET(Group.NAME, Level.ERROR),

  /**
   * Name was declared a species but contains infraspecific epithet
   */
  SUBSPECIES_ASSIGNED(Group.NAME, Level.ERROR),

  /**
   * lower case monomial match
   */
  LC_MONOMIAL(Group.NAME, Level.ERROR),

  /**
   * indetermined species name with "lower" rank than epithets
   */
  INDETERMINED(Group.NAME),

  /**
   * binomial with rank higher than species aggregate
   */
  HIGHER_RANK_BINOMIAL(Group.NAME, Level.ERROR),

  /**
   * question marks removed from name
   */
  QUESTION_MARKS_REMOVED(Group.NAME),

  /**
   * removed enclosing quotes from name
   */
  REPL_ENCLOSING_QUOTE(Group.NAME),

  /**
   * epithet without genus
   */
  MISSING_GENUS(Group.NAME, Level.ERROR),

  /**
   * dwc:nomenclaturalStatus could not be interpreted
   */
  NOMENCLATURAL_STATUS_INVALID(Group.NAME),

  /**
   * dwc:nomenclaturalCode could not be interpreted
   */
  NOMENCLATURAL_CODE_INVALID(Group.NAME, Level.ERROR),

  /**
   * A recombination with a basionym authorship which does not match the authorship of the linked basionym.
   */
  BASIONYM_AUTHOR_MISMATCH(Group.NAME, Level.ERROR),

  /**
   * Record has a original name (basionym) relationship which was derived from name & authorship comparison, but did not exist explicitly in the data.
   * Provisional catalogue specific issue.
   */
  xBASIONYM_DERIVED(Group.NAME),

  /**
   * There have been more than one accepted name in a homotypical basionym group of names.
   * Provisional catalogue specific issue.
   */
  CONFLICTING_BASIONYM_COMBINATION(Group.NAME, Level.ERROR),

  /**
   * A basionym that claims to have itself another basionym.
   */
  CHAINED_BASIONYM(Group.NAME, Level.ERROR),

  /**
   * A scientific name has been used to point to another record (synonym->accepted, combination->basionym)
   * which is not unique and refers to several records.
   */
  NAME_NOT_UNIQUE(Group.NAME, Level.ERROR),

  NAME_MATCH_VARIANT(Group.NAME, Level.WARNING),

  NAME_MATCH_INSERTED(Group.NAME, Level.WARNING),

  NAME_MATCH_AMBIGUOUS(Group.NAME, Level.WARNING),

  NAME_MATCH_NONE(Group.NAME, Level.WARNING),

  /**
   * A potential chresonym exists in the dataset.
   */
  POTENTIAL_CHRESONYM(Group.NAME),

  /**
   * A bi/trinomial name published earlier than the parent genus was published.
   * This might indicate that the name should rather be a recombination.
   */
  PUBLISHED_BEFORE_GENUS(Group.NAME),

  /**
   * The value for dwc:originalNameUsageID could not be resolved.
   */
  BASIONYM_ID_INVALID(Group.NAME, Level.ERROR),

  /**
   * dwc:taxonRank could not be interpreted
   */
  RANK_INVALID(Group.NAME, Level.ERROR),



//
// TAXON ISSUES
//

  /**
   * Multiple (accepted) taxa appear to be orthographic variants in the dataset.
   */
  POTENTIAL_VARIANT(Group.TAXON, Level.INFO),

  /**
   * Identifier for a taxon/name could not be resolved.
   */
  TAXON_ID_INVALID(Group.TAXON, Level.ERROR),

  /**
   * The value for dwc:parentNameUsageID could not be resolved.
   */
  PARENT_ID_INVALID(Group.TAXON, Level.ERROR),

  /**
   * The value for dwc:acceptedNameUsageID could not be resolved.
   */
  ACCEPTED_ID_INVALID(Group.TAXON, Level.ERROR),

  /**
   * Synonym lacking an accepted name.
   * These will be treated as bare names in the datastore
   */
  ACCEPTED_NAME_MISSING(Group.TAXON, Level.ERROR),

  /**
   * dwc:taxonomicStatus could not be interpreted
   */
  TAXONOMIC_STATUS_INVALID(Group.TAXON, Level.ERROR),

  /**
   * acef:lifezone contains values that cannot be interpreted
   */
  LIFEZONE_INVALID(Group.TAXON, Level.ERROR),

  /**
   * acef:IsFossil or acef:HasPreHolocene contains values that cannot be interpreted
   */
  IS_FOSSIL_INVALID(Group.TAXON, Level.ERROR),

  /**
   * acef:IsRecent or acef:HasModern contains values that cannot be interpreted
   */
  IS_RECENT_INVALID(Group.TAXON, Level.ERROR),

  /**
   * acef:LTSDate cannot be interpreted into a date
   */
  ACCORDING_TO_DATE_INVALID(Group.TAXON, Level.ERROR),

  /**
   * If a synonym points to another synonym as its accepted taxon the chain is resolved.
   */
  CHAINED_SYNONYM(Group.TAXON),

  /**
   * The child parent classification resulted into a cycle that needed to be resolved/cut.
   */
  PARENT_CYCLE(Group.TAXON, Level.ERROR),

  /**
   * An accepted taxon which has a synonym as its parent.
   * This relation is moved to the synonyms accepted name if possible, otherwise just removed.
   */
  SYNONYM_PARENT(Group.TAXON, Level.ERROR),

  /**
   * The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks.
   */
  CLASSIFICATION_RANK_ORDER_INVALID(Group.TAXON, Level.ERROR),

  /**
   * The denormalized classification could not be applied to the name usage.
   * For example if the id based classification has no ranks.
   */
  CLASSIFICATION_NOT_APPLIED(Group.TAXON),

  /**
   * The (accepted) bi/trinomial name does not match the parent genus/species.
   * For example the species Picea alba with a parent genus Abies is a mismatch.
   */
  PARENT_NAME_MISMATCH(Group.TAXON, Level.ERROR),

  /**
   * The taxonomic status was programmatically derived.
   * E.g. a synonym made a misapplied name or ambigous synonym based on name annotations.
   */
  DERIVED_TAXONOMIC_STATUS(Group.TAXON),

  /**
   * The given taxonomic status appears unlikely.
   * E.g. a misapplied name without any taxonomic remark indicating the source it was misapplied in.
   */
  TAXONOMIC_STATUS_DOUBTFUL(Group.TAXON),

  /**
   * A synonym which originally had associated data (distributions, vernacular names, bibliography)
   * but which was moved to its accepted taxon.
   */
  SYNONYM_DATA_MOVED(Group.TAXON, Level.INFO),

  /**
   * ACEF reference type values unparsable or missing
   */
  REFTYPE_INVALID(Group.TAXON, Level.ERROR),

  /**
   * The given species estimates are no parsable, positive integer numbers
   */
  ESTIMATES_INVALID(Group.TAXON, Level.ERROR),



//
// VERNACULAR ISSUES
//

  /**
   * A vernacular name extension record attached to this name usage is empty or clearly not a name.
   */
  VERNACULAR_NAME_INVALID(Group.VERNACULAR, Level.ERROR),

  /**
   * Transliterated vernacular name missing and generated automatically.
   * Assembled Catalogue specific issue.
   */
  VERNACULAR_NAME_TRANSLITERATED(Group.VERNACULAR, Level.INFO),



//
// DISTRIBUTION ISSUES
//

  /**
   * A distribution record is invalid and cannot be interpreted at all.
   */
  DISTRIBUTION_INVALID(Group.DISTRIBUTION, Level.ERROR),

  DISTRIBUTION_AREA_INVALID(Group.DISTRIBUTION, Level.ERROR),

  DISTRIBUTION_COUNTRY_INVALID(Group.DISTRIBUTION, Level.ERROR),

  DISTRIBUTION_STATUS_INVALID(Group.DISTRIBUTION, Level.ERROR),

  DISTRIBUTION_GAZETEER_INVALID(Group.DISTRIBUTION, Level.ERROR),



//
// REFERENCE ISSUES
//

  /**
   * The reference could not be parsed and broken down into a sensible record.
   */
  UNPARSABLE_REFERENCE(Group.REFERENCE, Level.ERROR),

  /**
   * The required reference type could not be parsed and a default has been assigned.
   */
  UNPARSABLE_REFERENCE_TYPE(Group.REFERENCE);





  /**
   * Level of importance.
   */
  public enum Level {
    ERROR,
    WARNING,
    INFO
  }

  /**
   * Issue group representing largely the effected entities.
   */
  public enum Group {
    DATA,
    NAME,
    TAXON,
    DISTRIBUTION,
    VERNACULAR,
    REFERENCE
  }

  public final Level level;

  public final Group group;

  Issue(Group group) {
    this(group, Level.WARNING);
  }

  Issue(Group group, Level level) {
    this.level = level;
    this.group = group;
  }
}
