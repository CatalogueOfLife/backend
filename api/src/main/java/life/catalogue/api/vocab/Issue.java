package life.catalogue.api.vocab;

import com.google.common.base.Preconditions;

import static life.catalogue.api.vocab.EntityType.*;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue {
  
  //
  // GENERAL DATA ISSUES
  //
  
  NOT_INTERPRETED(ANY, Level.ERROR,
      "A verbatim record could not be interpreted at all and has been ignored."),
  
  ESCAPED_CHARACTERS(ANY, Level.WARNING,
      "Escaped characters such as html entities have been unescaped during interpretation." +
      " The following escaped character formats are recognized: " +
      " 1) named, decimal or hexadecimal xml & html entities " +
      " 2) unicode entities U+0026 " +
      " 3) hexadecimal or octal java unicode entites " +
      " 4) CSS & ECMA Javascript entities"),

  REFERENCE_ID_INVALID(ANY, Level.ERROR,
      "Identifier for a reference could not be resolved. " +
      "This can be from any entity, e.g. name, distribution, vernaculars, etc"),
  
  ID_NOT_UNIQUE(ANY, Level.ERROR,
      "A taxon, name or reference with duplicate ids. " +
      "We remove the subsequent records but flag the first record with this issue."),

  URL_INVALID(ANY, Level.ERROR,
      "Any of the coldp:link, dc:references, acef:InfraSpeciesURL, acef:SpeciesURL or other terms that are supposed to be URLs cannot be interpreted into a valid URL"),
  
  PARTIAL_DATE(ANY, Level.WARNING,
      "Date string was provided a year, but month and/or day were absent"),

  PREVIOUS_LINE_SKIPPED(ANY, Level.WARNING,
      "The CSV record before this one had to be ignored because it had no content or wrong number of columns. Often indicates a serious delimiter problem"),

  SELF_REFERENCED_RELATION(ANY, Level.WARNING,
    "A taxon or name relation has an identical ID as the related ID and relates to itself. " +
      "We ignore such self referenced records but flag them with this issue."),

  //
  // NAME ISSUES
  //
  
  UNPARSABLE_NAME(NAME, Level.ERROR,
      "The scientific name string could not be parsed at all, but appears to be a parsable name type, " +
          "i.e. it is not classified as a virus or hybrid formula."),
  
  PARTIALLY_PARSABLE_NAME(NAME, Level.WARNING,
      "The beginning of the scientific name string was parsed, " +
          "but there is additional information in the string that was not understood."),
  
  UNPARSABLE_AUTHORSHIP(NAME, Level.ERROR,
      "The authorship string could not be parsed."),
  
  DOUBTFUL_NAME(NAME, Level.WARNING,
      "The name has been classified as doubtful by the parser."),
  
  INCONSISTENT_AUTHORSHIP(NAME, Level.ERROR,
      "Authorship found in scientificName and scientificNameAuthorship differ."),
  
  INCONSISTENT_NAME(NAME, Level.ERROR,
      "An parsed, but inconsistent name. " +
          "E.g. the rank of the name does not match the given name parts or suffices."),
  
  PARSED_NAME_DIFFERS(NAME, Level.WARNING,
      "The parsed scientific name string results in a different name then was given as the preferred atomized parts."),
  
  UNUSUAL_NAME_CHARACTERS(NAME, Level.WARNING,
      "The name parts contain unusual characters."),
  
  MULTI_WORD_EPITHET(NAME, Level.WARNING,
      "At least one epithet contains multiple words before parsing."),

  UPPERCASE_EPITHET(NAME, Level.WARNING,
      "At least one epithet contains upper case letters."),
  
  CONTAINS_REFERENCE(NAME, Level.WARNING,
      "The name contains a bibliographic reference"),
  
  NULL_EPITHET(NAME, Level.ERROR,
      "At least one epithet equals literal value \"null\" or \"none\"."),
  
  BLACKLISTED_EPITHET(NAME, Level.ERROR,
      "At least one epithet is blacklisted by the name parser"),
  
  SUBSPECIES_ASSIGNED(NAME, Level.ERROR,
      "Name was declared a species but contains infraspecific epithet"),
  
  LC_MONOMIAL(NAME, Level.ERROR,
      "Lower case monomial match"),
  
  INDETERMINED(NAME, Level.WARNING,
      "Indetermined species name with \"lower\" rank than epithets"),
  
  HIGHER_RANK_BINOMIAL(NAME, Level.ERROR,
      "Binomial with rank higher than species aggregate"),
  
  QUESTION_MARKS_REMOVED(NAME, Level.WARNING,
      "Question marks removed from name"),
  
  REPL_ENCLOSING_QUOTE(NAME, Level.WARNING,
      "Removed enclosing quotes from name"),
  
  MISSING_GENUS(NAME, Level.ERROR,
      "Epithet without genus"),
  
  NOMENCLATURAL_STATUS_INVALID(NAME, Level.WARNING,
      "The nomenclatural status could not be interpreted"),

  AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE(NAME, Level.INFO,
    "The name authorship contains nomenclatural status notes that are transferred to the name"),

  CONFLICTING_NOMENCLATURAL_STATUS(NAME, Level.WARNING,
    "The name authorship contains nomenclatural status notes which conflict with the explicitly given status"),

  NOMENCLATURAL_CODE_INVALID(NAME, Level.ERROR,
      "dwc:nomenclaturalCode could not be interpreted"),
  
  BASIONYM_AUTHOR_MISMATCH(NAME, Level.ERROR,
      "A recombination with a basionym authorship which does not match the authorship of the linked basionym"),
  
  BASIONYM_DERIVED(NAME, Level.INFO,
      "Record has a original name (basionym) relationship which was derived from name & authorship comparison, " +
          "but did not exist explicitly in the data. Extended release specific issue."),

  HOMOTYPIC_CONSOLIDATION_UNRESOLVED(NAME, Level.WARNING,
      "There have been more than one accepted name in a homotypic group of names which could not be resolved."),
  
  CHAINED_BASIONYM(NAME, Level.WARNING,
      "A basionym that claims itself to have another basionym."),
  
  NAME_NOT_UNIQUE(NAME, Level.ERROR,
      "A scientific name has been used to point to another record (synonym->accepted, combination->basionym) " +
          "which is not unique and refers to several records."),

  POTENTIAL_CHRESONYM(NAME, Level.WARNING,
      "A potential chresonym exists in the dataset."),
  
  PUBLISHED_BEFORE_GENUS(NAME, Level.WARNING,
      "A bi/trinomial name published earlier than the parent genus was published. " +
          "This might indicate that the name should rather be a recombination."),
  
  BASIONYM_ID_INVALID(NAME, Level.ERROR,
      "The name id pointing to a related original name could not be resolved."),
  
  RANK_INVALID(NAME, Level.ERROR,
      "dwc:taxonRank could not be interpreted"),
  
  UNMATCHED_NAME_BRACKETS(NAME, Level.ERROR,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  TRUNCATED_NAME(NAME, Level.WARNING,
      "The name or its authorship appears to be truncated."),

  DUPLICATE_NAME(NAME, Level.INFO,
      "Same name appears several time in dataset."),
  
  NAME_VARIANT(NAME, Level.INFO,
      "Multiple variants of the same name appear several times in the dataset."),

  AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE(NAME, Level.INFO,
    "The name authorship contains taxonomic notes that are transferred to the name usage"),


  //
  // TYPE MATERIAL ISSUES
  //
  TYPE_STATUS_INVALID(TYPE_MATERIAL, Level.WARNING,
          "type status could not be interpreted"),

  LAT_LON_INVALID(TYPE_MATERIAL, Level.WARNING,
      "decimal coordinate could not be interpreted"),

  ALTITUDE_INVALID(TYPE_MATERIAL, Level.WARNING,
      "altitude could not be interpreted. Should be a plain integer."),

  COUNTRY_INVALID(TYPE_MATERIAL, Level.WARNING,
      "decimal coordinate could not be interpreted"),

  //
  // TAXON ISSUES
  //
  
  TAXON_VARIANT(NAME_USAGE, Level.INFO,
      "Multiple (accepted) taxa appear to be orthographic variants in the dataset."),
  
  TAXON_ID_INVALID(NAME_USAGE, Level.ERROR,
      "Identifier for a taxon/name could not be resolved."),
  
  NAME_ID_INVALID(NAME_USAGE, Level.ERROR,
      "The name object for col:nameId could not be resolved."),

  PARENT_ID_INVALID(NAME_USAGE, Level.ERROR,
      "The value for dwc:parentNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_ID_INVALID(NAME_USAGE, Level.ERROR,
      "The value for dwc:acceptedNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_NAME_MISSING(NAME_USAGE, Level.ERROR,
      "Synonym lacking an accepted name. These will be treated as bare names in the datastore"),

  PARENT_SPECIES_MISSING(NAME_USAGE, Level.WARNING,
    "The accepted infraspecific name does not have an accepted species in it's classification."),

  TAXONOMIC_STATUS_INVALID(NAME_USAGE, Level.ERROR,
      "dwc:taxonomicStatus could not be interpreted"),
  
  PROVISIONAL_STATUS_INVALID(NAME_USAGE, Level.ERROR,
      "col:provisional is no boolean"),

  ENVIRONMENT_INVALID(NAME_USAGE, Level.WARNING,
      "environment contains values that cannot be interpreted"),
  
  IS_EXTINCT_INVALID(NAME_USAGE, Level.WARNING,
      "acef:IsExtinct contains values that cannot be interpreted"),

  NAME_CONTAINS_EXTINCT_SYMBOL(NAME_USAGE, Level.INFO,
    "The usage extinct flag was set because the name contained an extinct symbol"),

  GEOTIME_INVALID(NAME_USAGE, Level.WARNING,
      "The geochronological time given cannot be interpreted"),

  SCRUTINIZER_DATE_INVALID(NAME_USAGE, Level.ERROR,
      "acef:LTSDate cannot be interpreted into a date"),
  
  CHAINED_SYNONYM(NAME_USAGE, Level.WARNING,
      "If a synonym points to another synonym as its accepted taxon the chain is resolved."),
  
  PARENT_CYCLE(NAME_USAGE, Level.ERROR,
      "The child parent classification resulted into a cycle that needed to be resolved/cut."),
  
  SYNONYM_PARENT(NAME_USAGE, Level.ERROR,
      "An accepted taxon which has a synonym as its parent. " +
          "This relation is moved to the synonyms accepted name if possible, otherwise just removed."),
  
  CLASSIFICATION_RANK_ORDER_INVALID(NAME_USAGE, Level.ERROR,
      "The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks."),
  
  CLASSIFICATION_NOT_APPLIED(NAME_USAGE, Level.WARNING,
      "The denormalized classification could not be applied to the name usage. " +
          "For example if the id based classification has no ranks."),
  
  PARENT_NAME_MISMATCH(NAME_USAGE, Level.WARNING,
      "The (accepted) bi/trinomial name does not match the parent genus/species. " +
          "For example the species Picea alba with a parent genus Abies is a mismatch."),

  DERIVED_TAXONOMIC_STATUS(NAME_USAGE, Level.WARNING,
      "The taxonomic status was programmatically derived. " +
          "E.g. a synonym made a misapplied name or ambigous synonym based on name annotations."),
  
  TAXONOMIC_STATUS_DOUBTFUL(NAME_USAGE, Level.WARNING,
      "The given taxonomic status appears unlikely. " +
          "E.g. a misapplied name without any taxonomic remark indicating the source it was misapplied in."),
  
  SYNONYM_DATA_MOVED(NAME_USAGE, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was moved to its accepted taxon."),
  
  SYNONYM_DATA_REMOVED(NAME_USAGE, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was removed as it lacks an accepted taxon."),

  REFTYPE_INVALID(NAME_USAGE, Level.ERROR,
      "ACEF reference type values unparsable or missing"),

  ACCORDING_TO_CONFLICT(NAME_USAGE, Level.WARNING,
    "The taxonomic concept reference was given explicitly and implicitly through the names authorship"),

  //
  // VERNACULAR ISSUES
  //
  
  VERNACULAR_NAME_INVALID(VERNACULAR, Level.ERROR,
      "A vernacular name extension record attached to this name usage is empty or clearly not a name."),
  
  VERNACULAR_LANGUAGE_INVALID(VERNACULAR, Level.WARNING,
      "A vernacular name extension record attached to this name usage has an unparsable language."),

  VERNACULAR_SEX_INVALID(VERNACULAR, Level.WARNING,
          "A vernacular name extension record attached to this name usage has an unparsable sex."),

  VERNACULAR_COUNTRY_INVALID(VERNACULAR, Level.WARNING,
      "A vernacular name extension record attached to this name usage has an unparsable country."),

  VERNACULAR_NAME_TRANSLITERATED(VERNACULAR, Level.INFO,
      "Transliterated vernacular name missing and generated automatically. " +
          "Assembled Catalogue specific issue."),
  
  //
  // DISTRIBUTION ISSUES
  //
  
  DISTRIBUTION_INVALID(DISTRIBUTION, Level.ERROR,
      "A distribution record is invalid and cannot be interpreted at all."),
  
  DISTRIBUTION_AREA_INVALID(DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid area name/code."),
  
  DISTRIBUTION_STATUS_INVALID(DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid status code."),
  
  DISTRIBUTION_GAZETEER_INVALID(DISTRIBUTION, Level.ERROR,
      "A distribution record contains an unsupported gazeteer name."),
  
  //
  // MEDIA ISSUES
  //
  
  MEDIA_CREATED_DATE_INVALID(MEDIA, Level.ERROR,
      "The created date for the media record cannot be interpreted"),
  
  //
  // REFERENCE ISSUES
  //
  
  UNPARSABLE_YEAR(NAME, Level.WARNING,
      "The given year of publication of the name cannot be parsed into a sensible integer year."),
  
  UNLIKELY_YEAR(NAME, Level.WARNING,
      "The given year of publication of the name is impossible, i.e. either before Linnean times or after today."),

  MULTIPLE_PUBLISHED_IN_REFERENCES(REFERENCE, Level.WARNING,
      "There are multiple references for the original publication of a name."),
  
  UNPARSABLE_REFERENCE(REFERENCE, Level.ERROR,
      "The reference could not be parsed and broken down into a sensible record."),
  
  UNPARSABLE_REFERENCE_TYPE(REFERENCE, Level.WARNING,
      "The required reference type could not be parsed and a default has been assigned."),
  
  UNMATCHED_REFERENCE_BRACKETS(REFERENCE, Level.WARNING,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  CITATION_CONTAINER_TITLE_UNPARSED(REFERENCE, Level.INFO,
      "Value for container title was stored literally when it probably included additional information, e.g. pages"),
  
  CITATION_DETAILS_UNPARSED(REFERENCE, Level.INFO,
      "Value for reference details was stored literally and placed in CSL page"),
  
  CITATION_AUTHORS_UNPARSED(REFERENCE, Level.INFO,
      "Value for reference authors was stored literally and not broken down into individual authors"),

  CITATION_UNPARSED(REFERENCE, Level.INFO,
      "Value for citation was accepted as-is"),

  //
  // TREATMENT ISSUES
  //
  UNPARSABLE_TREATMENT(TREATMENT, Level.ERROR,
      "The treatment document could not be read."),
  UNPARSABLE_TREAMENT_FORMAT(TREATMENT, Level.ERROR,
    "The required treatment format could not be parsed."),

  //
  // ESTIMATE ISSUES
  //
  ESTIMATE_INVALID(ESTIMATE, Level.ERROR,
      "The species estimate could not be parsed to a positive integer."),
  ESTIMATE_TYPE_INVALID(ESTIMATE, Level.WARNING,
      "The species estimate type could not be parsed."),


  //
  // Collect NEW ISSUES here - we need to keep ordinal id's fixed otherwise we need to reindex ElasticSearch
  //
  INVISIBLE_CHARACTERS(ANY, Level.WARNING,
    "Invisible characters such as control characters have been removed "
    + "or exotic alternatives for spaces been replaced with their canonical form."),

  HOMOGLYPH_CHARACTERS(ANY, Level.WARNING,
    "Potential homoglyphs of the latin alphabet are encountered."
    + "In vernacular names, authors or references this can be perfectly fine content if other scripts than latin, e.g. cyrillic or greek are used."),

  RELATED_NAME_MISSING(SPECIES_INTERACTION, Level.WARNING,
    "Species interaction without a related name."
    + "Neither relatedTaxonID nor relatedScientificName is given with a valid value."),

  DIACRITIC_CHARACTERS(ANY, Level.WARNING,
    "Seperate diacritic characters such as the acute, grave or circumflex, are present on their own. They should only exist in combination with a letter."),

  MULTI_WORD_MONOMIAL(NAME, Level.WARNING,
    "At least one monomial of the parsed name contains multiple words."),

  WRONG_MONOMIAL_CASE(NAME, Level.WARNING,
    "At least one monomial of the parsed name in not using title case."),

  AUTHORSHIP_REMOVED(NAME, Level.WARNING,
    "The interpreted authorship was removed because it was not considered appropriate."),

  DOI_NOT_FOUND(REFERENCE, Level.WARNING,
    "The given DOI does not exist in Crossref."),

  DOI_UNRESOLVED(REFERENCE, Level.WARNING,
    "The given DOI could not be resolved by Crossref. This might be a temporary problem."),

  TYPE_MATERIAL_SEX_INVALID(TYPE_MATERIAL, Level.WARNING,
    "A type material record attached to this name has an unparsable sex."),

  IDENTIFIER_WITHOUT_SCOPE(ANY, Level.WARNING,
    "The alternative identifier for a taxon, name or reference is lacking an identifier scope."),

  HOMOTYPIC_CONSOLIDATION(NAME_USAGE, Level.INFO,
    "Several accepted names existed which are considered homotypic names and which have been resolved into a single one."),

  SYNC_OUTSIDE_TARGET(NAME_USAGE, Level.WARNING,
    "Names from a sector sync have been placed outside of the configured target taxon of the project."),

  MULTIPLE_BASIONYMS(NAME, Level.WARNING,
    "There have been more than one name that appears to be the basionym / original combination. No homotypic consolidation can be performed."),

  PUBLISHED_YEAR_CONFLICT(NAME, Level.WARNING,
    "The given year of publication of the name conflicts with year of the authorship."),

  MULTILINE_RECORD(ANY, Level.WARNING,
    "The verbatim record spans multiple lines without escaping or quoting. It has been processed, but should be taken with caution as it does not correspond with the specifications."),

  NOTHO_INVALID(NAME, Level.WARNING,
    "The notho name property could not be interpreted"),

  ORIGINAL_SPELLING_INVALID(NAME, Level.WARNING,
    "The original spelling name property could not be interpreted as a boolean"),

  UNINOMIAL_FIELD_MISPLACED(NAME, Level.INFO,
    "The name of a uninomial is misplaced in the wrong field, e.g. genus."),

  INFRAGENERIC_FIELD_MISPLACED(NAME, Level.INFO,
    "The name of an infrageneric name is misplaced in the wrong field, e.g. uninomial."),

  ORDINAL_INVALID(NAME_USAGE, Level.WARNING,
    "The ordinal to sort taxon children or taxon properties is not a valid integer number."),

  GENDER_INVALID(NAME, Level.WARNING,
    "The gender valid of the name could not be interpreted."),

  GENDER_AGREEMENT_NOT_APPLICABLE(NAME, Level.WARNING,
    "The gender agreement flag can only be given to bi- or trinomials with a terminal epithet."),

  NOTHO_NOT_APPLICABLE(NAME, Level.WARNING,
    "The notho name property refers to a non existing part of the name."),

  VERNACULAR_PREFERRED(VERNACULAR, Level.WARNING,
    "The vernacular name preferred property could not be interpreted as a boolean."),

  DOI_INVALID(ANY, Level.WARNING,
    "The given DOI is syntactically invalid."),

  RANK_NAME_SUFFIX_CONFLICT(NAME, Level.WARNING,
    "The name ending does suggest a different rank than given for the name."),

  AUTHORSHIP_UNLIKELY(NAME, Level.WARNING,
    "The authorship of the name is unlikely a true author, e.g. resembles part of the scientific name."),

  NAME_PHRASE_UNLIKELY(NAME_USAGE, Level.WARNING,
    "The name phrase is unlikely, e.g. is just a number or a boolean value."),

  VERNACULAR_NAME_UNLIKELY(VERNACULAR, Level.WARNING,
    "A vernacular name which is rather unlikely a true, single vernacular name."),

  ;

  /**
   * Level of importance.
   */
  public enum Level {
    ERROR,
    WARNING,
    INFO
  }
  
  public final Level level;
  
  public final EntityType group;
  
  public final String description;
  
  Issue(EntityType group, Level level, String description) {
    this.level = Preconditions.checkNotNull(level);
    this.group = Preconditions.checkNotNull(group);
    this.description = description;
  }
}
