package org.col.api.vocab;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue {
  
  //
  // GENERAL DATA ISSUES
  //
  
  NOT_INTERPRETED(Group.DATA, Level.ERROR,
      "A verbatim record could not be interpreted at all and has been ignored."),
  
  ESCAPED_CHARACTERS(Group.DATA, Level.WARNING,
      "Escaped characters such as html entities have been unescaped during interpretation." +
      " The following escaped character formats are recognized: " +
      " 1) named, decimal or hexadecimal xml & html entities " +
      " 2) unicode entities U+0026 " +
      " 3) hexadecimal or octal java unicode entites " +
      " 4) CSS & ECMA Javascript entities"),
  
  REFERENCE_ID_INVALID(Group.DATA, Level.ERROR,
      "Identifier for a reference could not be resolved. " +
      "This can be from any entity, e.g. name, distribution, vernaculars, etc"),
  
  ID_NOT_UNIQUE(Group.DATA, Level.ERROR,
      "A taxon, name or reference with duplicate ids. " +
      "We remove the subsequent records but flag the first record with this issue."),
  
  URL_INVALID(Group.DATA, Level.ERROR,
      "dc:references, acef:InfraSpeciesURL or acef:SpeciesURL cannot be interpreted into a valid URL"),
  
  PARTIAL_DATE(Group.DATA, Level.WARNING,
      "Date string was provided a year, but month and/or day were absent"),
  
  //
  // NAME ISSUES
  //
  
  UNPARSABLE_NAME(Group.NAME, Level.ERROR,
      "The scientific name string could not be parsed at all, but appears to be a parsable name type, " +
          "i.e. it is not classified as a virus or hybrid formula."),
  
  PARTIALLY_PARSABLE_NAME(Group.NAME, Level.WARNING,
      "The beginning of the scientific name string was parsed, " +
          "but there is additional information in the string that was not understood."),
  
  UNPARSABLE_AUTHORSHIP(Group.NAME, Level.ERROR,
      "The authorship string could not be parsed."),
  
  DOUBTFUL_NAME(Group.NAME, Level.WARNING,
      "The name has been classified as doubtful by the parser."),
  
  INCONSISTENT_AUTHORSHIP(Group.NAME, Level.ERROR,
      "Authorship found in scientificName and scientificNameAuthorship differ."),
  
  INCONSISTENT_NAME(Group.NAME, Level.ERROR,
      "An parsed, but inconsistent name. " +
          "E.g. the rank of the name does not match the given name parts or suffices."),
  
  PARSED_NAME_DIFFERS(Group.NAME, Level.WARNING,
      "The parsed scientific name string results in a different name then was given as already atomized parts."),
  
  UNUSUAL_NAME_CHARACTERS(Group.NAME, Level.WARNING,
      "The name parts contain unusual characters."),
  
  NULL_EPITHET(Group.NAME, Level.ERROR,
      "At least one epithet equals literal value \"null\" or \"none\"."),
  
  SUBSPECIES_ASSIGNED(Group.NAME, Level.ERROR,
      "Name was declared a species but contains infraspecific epithet"),
  
  LC_MONOMIAL(Group.NAME, Level.ERROR,
      "Lower case monomial match"),
  
  INDETERMINED(Group.NAME, Level.WARNING,
      "Indetermined species name with \"lower\" rank than epithets"),
  
  HIGHER_RANK_BINOMIAL(Group.NAME, Level.ERROR,
      "Binomial with rank higher than species aggregate"),
  
  QUESTION_MARKS_REMOVED(Group.NAME, Level.WARNING,
      "Question marks removed from name"),
  
  REPL_ENCLOSING_QUOTE(Group.NAME, Level.WARNING,
      "Removed enclosing quotes from name"),
  
  MISSING_GENUS(Group.NAME, Level.ERROR,
      "Epithet without genus"),
  
  NOMENCLATURAL_STATUS_INVALID(Group.NAME, Level.WARNING,
      "dwc:nomenclaturalStatus could not be interpreted"),
  
  NOMENCLATURAL_CODE_INVALID(Group.NAME, Level.ERROR,
      "dwc:nomenclaturalCode could not be interpreted"),
  
  BASIONYM_AUTHOR_MISMATCH(Group.NAME, Level.ERROR,
      "A recombination with a basionym authorship which does not match the authorship of the linked basionym"),
  
//  BASIONYM_DERIVED(Group.NAME,
//      "Record has a original name (basionym) relationship which was derived from name & authorship comparison, " +
//          "but did not exist explicitly in the data. Provisional catalogue specific issue."),
  
  CONFLICTING_BASIONYM_COMBINATION(Group.NAME, Level.ERROR,
      "There have been more than one accepted name in a homotypical basionym group of names. " +
          "Provisional catalogue specific issue."),
  
  CHAINED_BASIONYM(Group.NAME, Level.ERROR,
      "A basionym that claims to have itself another basionym."),
  
  NAME_NOT_UNIQUE(Group.NAME, Level.ERROR,
      "A scientific name has been used to point to another record (synonym->accepted, combination->basionym) " +
          "which is not unique and refers to several records."),
  
  NAME_MATCH_INSERTED(Group.NAME, Level.INFO, "The name was never seen before and inserted into the Names Index for the first time"),

  NAME_MATCH_VARIANT(Group.NAME, Level.INFO, "The name matches an orthographic variant of the name, authorship and/or rank " +
      "already found in the names index and which is considered to be still the same name."),
  
  NAME_MATCH_AMBIGUOUS(Group.NAME, Level.WARNING, "Multiple matching names found in the Names Index."),
  
  NAME_MATCH_NONE(Group.NAME, Level.WARNING, "Name not found in the Names Index."),
  
  POTENTIAL_CHRESONYM(Group.NAME, Level.WARNING,
      "A potential chresonym exists in the dataset."),
  
  PUBLISHED_BEFORE_GENUS(Group.NAME, Level.WARNING,
      "A bi/trinomial name published earlier than the parent genus was published. " +
          "This might indicate that the name should rather be a recombination."),
  
  BASIONYM_ID_INVALID(Group.NAME, Level.ERROR,
      "The value for dwc:originalNameUsageID could not be resolved."),
  
  RANK_INVALID(Group.NAME, Level.ERROR,
      "dwc:taxonRank could not be interpreted"),
  
  UNMATCHED_NAME_BRACKETS(Group.NAME, Level.ERROR,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  TRUNCATED_NAME(Group.NAME, Level.WARNING,
      "The name or its authorship appears to be truncated."),

  DUPLICATE_NAME(Group.TAXON, Level.INFO,
      "Same name appears several time in dataset."),
  
  //
  // TAXON ISSUES
  //
  
  POTENTIAL_VARIANT(Group.TAXON, Level.INFO,
      "Multiple (accepted) taxa appear to be orthographic variants in the dataset."),
  
  TAXON_ID_INVALID(Group.TAXON, Level.ERROR,
      "Identifier for a taxon/name could not be resolved."),
  
  NAME_ID_INVALID(Group.TAXON, Level.ERROR,
      "The name object for col:nameId could not be resolved."),

  PARENT_ID_INVALID(Group.TAXON, Level.ERROR,
      "The value for dwc:parentNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_ID_INVALID(Group.TAXON, Level.ERROR,
      "The value for dwc:acceptedNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_NAME_MISSING(Group.TAXON, Level.ERROR,
      "Synonym lacking an accepted name. These will be treated as bare names in the datastore"),
  
  TAXONOMIC_STATUS_INVALID(Group.TAXON, Level.ERROR,
      "dwc:taxonomicStatus could not be interpreted"),
  
  LIFEZONE_INVALID(Group.TAXON, Level.WARNING,
      "acef:lifezone contains values that cannot be interpreted"),
  
  IS_FOSSIL_INVALID(Group.TAXON, Level.WARNING,
      "acef:IsFossil or acef:HasPreHolocene contains values that cannot be interpreted"),
  
  IS_RECENT_INVALID(Group.TAXON, Level.WARNING,
      "acef:IsRecent or acef:HasModern contains values that cannot be interpreted"),
  
  ACCORDING_TO_DATE_INVALID(Group.TAXON, Level.ERROR,
      "acef:LTSDate cannot be interpreted into a date"),
  
  CHAINED_SYNONYM(Group.TAXON, Level.ERROR,
      "If a synonym points to another synonym as its accepted taxon the chain is resolved."),
  
  PARENT_CYCLE(Group.TAXON, Level.ERROR,
      "The child parent classification resulted into a cycle that needed to be resolved/cut."),
  
  SYNONYM_PARENT(Group.TAXON, Level.ERROR,
      "An accepted taxon which has a synonym as its parent. " +
          "This relation is moved to the synonyms accepted name if possible, otherwise just removed."),
  
  CLASSIFICATION_RANK_ORDER_INVALID(Group.TAXON, Level.ERROR,
      "The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks."),
  
  CLASSIFICATION_NOT_APPLIED(Group.TAXON, Level.WARNING,
      "The denormalized classification could not be applied to the name usage. " +
          "For example if the id based classification has no ranks."),
  
  PARENT_NAME_MISMATCH(Group.TAXON, Level.ERROR,
      "The (accepted) bi/trinomial name does not match the parent genus/species. " +
          "For example the species Picea alba with a parent genus Abies is a mismatch."),
  
  DERIVED_TAXONOMIC_STATUS(Group.TAXON, Level.WARNING,
      "The taxonomic status was programmatically derived. " +
          "E.g. a synonym made a misapplied name or ambigous synonym based on name annotations."),
  
  TAXONOMIC_STATUS_DOUBTFUL(Group.TAXON, Level.WARNING,
      "The given taxonomic status appears unlikely. " +
          "E.g. a misapplied name without any taxonomic remark indicating the source it was misapplied in."),
  
  SYNONYM_DATA_MOVED(Group.TAXON, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was moved to its accepted taxon."),
  
  SYNONYM_DATA_REMOVED(Group.TAXON, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was removed as it lacks an accepted taxon."),

  REFTYPE_INVALID(Group.TAXON, Level.ERROR,
      "ACEF reference type values unparsable or missing"),
  
  ESTIMATES_INVALID(Group.TAXON, Level.ERROR,
      "The given species estimates are no parsable, positive integer numbers"),
  
  //
  // VERNACULAR ISSUES
  //
  
  VERNACULAR_NAME_INVALID(Group.VERNACULAR, Level.ERROR,
      "A vernacular name extension record attached to this name usage is empty or clearly not a name."),
  
  VERNACULAR_NAME_TRANSLITERATED(Group.VERNACULAR, Level.INFO,
      "Transliterated vernacular name missing and generated automatically. " +
          "Assembled Catalogue specific issue."),
  
  //
  // DISTRIBUTION ISSUES
  //
  
  DISTRIBUTION_INVALID(Group.DISTRIBUTION, Level.ERROR,
      "A distribution record is invalid and cannot be interpreted at all."),
  
  DISTRIBUTION_AREA_INVALID(Group.DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid area name/code."),
  
  DISTRIBUTION_STATUS_INVALID(Group.DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid status code."),
  
  DISTRIBUTION_GAZETEER_INVALID(Group.DISTRIBUTION, Level.ERROR,
      "A distribution record contains an unsupported gazeteer name."),
  
  //
  // MEDIA ISSUES
  //
  
  MEDIA_CREATED_DATE_INVALID(Group.MEDIA, Level.ERROR,
      "The created date for the media record cannot be interpreted"),
  
  //
  // REFERENCE ISSUES
  //
  
  MULTIPLE_PUBLISHED_IN_REFERENCES(Group.REFERENCE, Level.WARNING,
      "There are multiple references for the original publication of a name."),
  
  UNPARSABLE_REFERENCE(Group.REFERENCE, Level.ERROR,
      "The reference could not be parsed and broken down into a sensible record."),
  
  UNPARSABLE_REFERENCE_TYPE(Group.REFERENCE, Level.WARNING,
      "The required reference type could not be parsed and a default has been assigned."),
  
  UNMATCHED_REFERENCE_BRACKETS(Group.REFERENCE, Level.WARNING,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  CITATION_CONTAINER_TITLE_UNPARSED(Group.REFERENCE, Level.INFO,
      "Value for container title was accepted as-is"),
  
  CITATION_UNPARSED(Group.REFERENCE, Level.INFO,
      "Value for citation was accepted as-is");
  

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
    DESCRIPTION,
    DISTRIBUTION,
    MEDIA,
    VERNACULAR,
    REFERENCE
  }
  
  public final Level level;
  
  public final Group group;
  
  public final String description;

  Issue(Group group, Level level, String description) {
    this.level = level;
    this.group = group;
    this.description = description;
  }
}
