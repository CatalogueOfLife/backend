package org.col.api.vocab;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import static org.col.api.vocab.EntityType.*;

/**
 * Enumeration of issues for all processed names encountered during processing.
 */
public enum Issue implements KeyedEnum {
  
  //
  // GENERAL DATA ISSUES
  //
  
  NOT_INTERPRETED(0, ANY, Level.ERROR,
      "A verbatim record could not be interpreted at all and has been ignored."),
  
  ESCAPED_CHARACTERS(1, ANY, Level.WARNING,
      "Escaped characters such as html entities have been unescaped during interpretation." +
      " The following escaped character formats are recognized: " +
      " 1) named, decimal or hexadecimal xml & html entities " +
      " 2) unicode entities U+0026 " +
      " 3) hexadecimal or octal java unicode entites " +
      " 4) CSS & ECMA Javascript entities"),
  
  REFERENCE_ID_INVALID(2, ANY, Level.ERROR,
      "Identifier for a reference could not be resolved. " +
      "This can be from any entity, e.g. name, distribution, vernaculars, etc"),
  
  ID_NOT_UNIQUE(3, ANY, Level.ERROR,
      "A taxon, name or reference with duplicate ids. " +
      "We remove the subsequent records but flag the first record with this issue."),
  
  URL_INVALID(4, ANY, Level.ERROR,
      "dc:references, acef:InfraSpeciesURL or acef:SpeciesURL cannot be interpreted into a valid URL"),
  
  PARTIAL_DATE(5, ANY, Level.WARNING,
      "Date string was provided a year, but month and/or day were absent"),
  
  //
  // NAME ISSUES
  //
  
  UNPARSABLE_NAME(100, NAME, Level.ERROR,
      "The scientific name string could not be parsed at all, but appears to be a parsable name type, " +
          "i.e. it is not classified as a virus or hybrid formula."),
  
  PARTIALLY_PARSABLE_NAME(101, NAME, Level.WARNING,
      "The beginning of the scientific name string was parsed, " +
          "but there is additional information in the string that was not understood."),
  
  UNPARSABLE_AUTHORSHIP(102, NAME, Level.ERROR,
      "The authorship string could not be parsed."),
  
  DOUBTFUL_NAME(103, NAME, Level.WARNING,
      "The name has been classified as doubtful by the parser."),
  
  INCONSISTENT_AUTHORSHIP(104, NAME, Level.ERROR,
      "Authorship found in scientificName and scientificNameAuthorship differ."),
  
  INCONSISTENT_NAME(105, NAME, Level.ERROR,
      "An parsed, but inconsistent name. " +
          "E.g. the rank of the name does not match the given name parts or suffices."),
  
  PARSED_NAME_DIFFERS(106, NAME, Level.WARNING,
      "The parsed scientific name string results in a different name then was given as already atomized parts."),
  
  UNUSUAL_NAME_CHARACTERS(107, NAME, Level.WARNING,
      "The name parts contain unusual characters."),
  
  MULTI_WORD_EPITHET(108, NAME, Level.WARNING,
      "At least one epithet contains multiple words before parsing."),

  UPPERCASE_EPITHET(109, NAME, Level.WARNING,
      "At least one epithet contains upper case letters."),
  
  CONTAINS_REFERENCE(110, NAME, Level.WARNING,
      "The name contains a bibliographic reference"),
  
  NULL_EPITHET(111, NAME, Level.ERROR,
      "At least one epithet equals literal value \"null\" or \"none\"."),
  
  BLACKLISTED_EPITHET(112, NAME, Level.ERROR,
      "At least one epithet is blacklisted by the name parser"),
  
  SUBSPECIES_ASSIGNED(113, NAME, Level.ERROR,
      "Name was declared a species but contains infraspecific epithet"),
  
  LC_MONOMIAL(114, NAME, Level.ERROR,
      "Lower case monomial match"),
  
  INDETERMINED(115, NAME, Level.WARNING,
      "Indetermined species name with \"lower\" rank than epithets"),
  
  HIGHER_RANK_BINOMIAL(116, NAME, Level.ERROR,
      "Binomial with rank higher than species aggregate"),
  
  QUESTION_MARKS_REMOVED(117, NAME, Level.WARNING,
      "Question marks removed from name"),
  
  REPL_ENCLOSING_QUOTE(118, NAME, Level.WARNING,
      "Removed enclosing quotes from name"),
  
  MISSING_GENUS(119, NAME, Level.ERROR,
      "Epithet without genus"),
  
  NOMENCLATURAL_STATUS_INVALID(120, NAME, Level.WARNING,
      "dwc:nomenclaturalStatus could not be interpreted"),
  
  NOMENCLATURAL_CODE_INVALID(121, NAME, Level.ERROR,
      "dwc:nomenclaturalCode could not be interpreted"),
  
  TYPE_STATUS_INVALID(122, NAME, Level.WARNING,
      "typeStatus could not be interpreted"),

  BASIONYM_AUTHOR_MISMATCH(123, NAME, Level.ERROR,
      "A recombination with a basionym authorship which does not match the authorship of the linked basionym"),
  
  BASIONYM_DERIVED(124, NAME, Level.INFO,
      "Record has a original name (basionym) relationship which was derived from name & authorship comparison, " +
          "but did not exist explicitly in the data. Provisional catalogue specific issue."),
  
  CONFLICTING_BASIONYM_COMBINATION(125, NAME, Level.ERROR,
      "There have been more than one accepted name in a homotypical basionym group of names. " +
          "Provisional catalogue specific issue."),
  
  CHAINED_BASIONYM(126, NAME, Level.ERROR,
      "A basionym that claims to have itself another basionym."),
  
  NAME_NOT_UNIQUE(127, NAME, Level.ERROR,
      "A scientific name has been used to point to another record (synonym->accepted, combination->basionym) " +
          "which is not unique and refers to several records."),
  
  NAME_MATCH_INSERTED(128, NAME, Level.INFO, "The name was never seen before and inserted into the Names Index for the first time"),

  NAME_MATCH_VARIANT(129, NAME, Level.INFO, "The name matches an orthographic variant of the name, authorship and/or rank " +
      "already found in the names index and which is considered to be still the same name."),
  
  NAME_MATCH_AMBIGUOUS(130, NAME, Level.WARNING, "Multiple matching names found in the Names Index."),
  
  NAME_MATCH_NONE(131, NAME, Level.WARNING, "Name not found in the Names Index."),
  
  POTENTIAL_CHRESONYM(132, NAME, Level.WARNING,
      "A potential chresonym exists in the dataset."),
  
  PUBLISHED_BEFORE_GENUS(133, NAME, Level.WARNING,
      "A bi/trinomial name published earlier than the parent genus was published. " +
          "This might indicate that the name should rather be a recombination."),
  
  BASIONYM_ID_INVALID(134, NAME, Level.ERROR,
      "The value for dwc:originalNameUsageID could not be resolved."),
  
  RANK_INVALID(135, NAME, Level.ERROR,
      "dwc:taxonRank could not be interpreted"),
  
  UNMATCHED_NAME_BRACKETS(136, NAME, Level.ERROR,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  TRUNCATED_NAME(137, NAME, Level.WARNING,
      "The name or its authorship appears to be truncated."),

  DUPLICATE_NAME(138, NAME, Level.INFO,
      "Same name appears several time in dataset."),
  
  NAME_VARIANT(139, NAME, Level.INFO,
      "Multiple variants of the same name appear several times in the dataset."),
  
  //
  // TAXON ISSUES
  //
  
  TAXON_VARIANT(200, NAME_USAGE, Level.INFO,
      "Multiple (accepted) taxa appear to be orthographic variants in the dataset."),
  
  TAXON_ID_INVALID(201, NAME_USAGE, Level.ERROR,
      "Identifier for a taxon/name could not be resolved."),
  
  NAME_ID_INVALID(202, NAME_USAGE, Level.ERROR,
      "The name object for col:nameId could not be resolved."),

  PARENT_ID_INVALID(203, NAME_USAGE, Level.ERROR,
      "The value for dwc:parentNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_ID_INVALID(204, NAME_USAGE, Level.ERROR,
      "The value for dwc:acceptedNameUsageID could not be resolved or is missing."),
  
  ACCEPTED_NAME_MISSING(205, NAME_USAGE, Level.ERROR,
      "Synonym lacking an accepted name. These will be treated as bare names in the datastore"),
  
  TAXONOMIC_STATUS_INVALID(206, NAME_USAGE, Level.ERROR,
      "dwc:taxonomicStatus could not be interpreted"),
  
  PROVISIONAL_STATUS_INVALID(207, NAME_USAGE, Level.ERROR,
      "col:provisional is no boolean"),
  
  LIFEZONE_INVALID(208, NAME_USAGE, Level.WARNING,
      "acef:lifezone contains values that cannot be interpreted"),
  
  IS_EXTINCT_INVALID(209, NAME_USAGE, Level.WARNING,
      "acef:IsExtinct contains values that cannot be interpreted"),
  
  GEOTIME_INVALID(210, NAME_USAGE, Level.WARNING,
      "The geochronological time given cannot be interpreted"),

  ACCORDING_TO_DATE_INVALID(211, NAME_USAGE, Level.ERROR,
      "acef:LTSDate cannot be interpreted into a date"),
  
  CHAINED_SYNONYM(212, NAME_USAGE, Level.ERROR,
      "If a synonym points to another synonym as its accepted taxon the chain is resolved."),
  
  PARENT_CYCLE(213, NAME_USAGE, Level.ERROR,
      "The child parent classification resulted into a cycle that needed to be resolved/cut."),
  
  SYNONYM_PARENT(214, NAME_USAGE, Level.ERROR,
      "An accepted taxon which has a synonym as its parent. " +
          "This relation is moved to the synonyms accepted name if possible, otherwise just removed."),
  
  CLASSIFICATION_RANK_ORDER_INVALID(215, NAME_USAGE, Level.ERROR,
      "The given ranks of the names in the classification hierarchy do not follow the hierarchy of ranks."),
  
  CLASSIFICATION_NOT_APPLIED(216, NAME_USAGE, Level.WARNING,
      "The denormalized classification could not be applied to the name usage. " +
          "For example if the id based classification has no ranks."),
  
  PARENT_NAME_MISMATCH(217, NAME_USAGE, Level.ERROR,
      "The (accepted) bi/trinomial name does not match the parent genus/species. " +
          "For example the species Picea alba with a parent genus Abies is a mismatch."),
  
  DERIVED_TAXONOMIC_STATUS(218, NAME_USAGE, Level.WARNING,
      "The taxonomic status was programmatically derived. " +
          "E.g. a synonym made a misapplied name or ambigous synonym based on name annotations."),
  
  TAXONOMIC_STATUS_DOUBTFUL(219, NAME_USAGE, Level.WARNING,
      "The given taxonomic status appears unlikely. " +
          "E.g. a misapplied name without any taxonomic remark indicating the source it was misapplied in."),
  
  SYNONYM_DATA_MOVED(220, NAME_USAGE, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was moved to its accepted taxon."),
  
  SYNONYM_DATA_REMOVED(221, NAME_USAGE, Level.INFO,
      "A synonym which originally had some associated data (descriptions distributions, media items, vernacular names, bibliography) " +
          "but which was removed as it lacks an accepted taxon."),

  REFTYPE_INVALID(222, NAME_USAGE, Level.ERROR,
      "ACEF reference type values unparsable or missing"),
  
  //
  // VERNACULAR ISSUES
  //
  
  VERNACULAR_NAME_INVALID(300, VERNACULAR, Level.ERROR,
      "A vernacular name extension record attached to this name usage is empty or clearly not a name."),
  
  VERNACULAR_LANGUAGE_INVALID(301, VERNACULAR, Level.WARNING,
      "A vernacular name extension record attached to this name usage has an unparsable language."),
  
  VERNACULAR_COUNTRY_INVALID(302, VERNACULAR, Level.WARNING,
      "A vernacular name extension record attached to this name usage has an unparsable country."),

  VERNACULAR_NAME_TRANSLITERATED(303, VERNACULAR, Level.INFO,
      "Transliterated vernacular name missing and generated automatically. " +
          "Assembled Catalogue specific issue."),
  
  //
  // DISTRIBUTION ISSUES
  //
  
  DISTRIBUTION_INVALID(400, DISTRIBUTION, Level.ERROR,
      "A distribution record is invalid and cannot be interpreted at all."),
  
  DISTRIBUTION_AREA_INVALID(401, DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid area name/code."),
  
  DISTRIBUTION_STATUS_INVALID(402, DISTRIBUTION, Level.ERROR,
      "A distribution record contains an invalid status code."),
  
  DISTRIBUTION_GAZETEER_INVALID(403, DISTRIBUTION, Level.ERROR,
      "A distribution record contains an unsupported gazeteer name."),
  
  //
  // MEDIA ISSUES
  //
  
  MEDIA_CREATED_DATE_INVALID(500, MEDIA, Level.ERROR,
      "The created date for the media record cannot be interpreted"),
  
  //
  // REFERENCE ISSUES
  //
  
  UNPARSABLE_YEAR(600, REFERENCE, Level.WARNING,
      "The given year of publication cannot be parsed into a sensible integer year."),
  
  UNLIKELY_YEAR(601, REFERENCE, Level.WARNING,
      "The given year of publication is unlikely to be real."),
  
  MULTIPLE_PUBLISHED_IN_REFERENCES(602, REFERENCE, Level.WARNING,
      "There are multiple references for the original publication of a name."),
  
  UNPARSABLE_REFERENCE(603, REFERENCE, Level.ERROR,
      "The reference could not be parsed and broken down into a sensible record."),
  
  UNPARSABLE_REFERENCE_TYPE(604, REFERENCE, Level.WARNING,
      "The required reference type could not be parsed and a default has been assigned."),
  
  UNMATCHED_REFERENCE_BRACKETS(605, REFERENCE, Level.WARNING,
      "The name (authorship) contains unmatched brackets, i.e. it misses closing brackets usually indicating truncated data."),
  
  CITATION_CONTAINER_TITLE_UNPARSED(606, REFERENCE, Level.INFO,
      "Value for container title was stored literally when it probably included additional information, e.g. pages"),
  
  CITATION_DETAILS_UNPARSED(607, REFERENCE, Level.INFO,
      "Value for reference details was stored literally and placed in CSL page"),
  
  CITATION_AUTHORS_UNPARSED(608, REFERENCE, Level.INFO,
      "Value for reference authors was stored literally and not broken down into individual authors"),

  CITATION_UNPARSED(609, REFERENCE, Level.INFO,
      "Value for citation was accepted as-is");
  
  private static final Int2ObjectMap<Issue> KEYS = new Int2ObjectOpenHashMap<>();
  static {
    for (Issue i : Issue.values()) {
      KEYS.put(i.key, i);
    }
  }
  
  /**
   * Level of importance.
   */
  public enum Level {
    ERROR,
    WARNING,
    INFO
  }
  
  public final int key;

  public final Level level;
  
  public final EntityType group;
  
  public final String description;
  
  public int getKey() {
    return key;
  }
  
  Issue(int key, EntityType group, Level level, String description) {
    this.key = key;
    this.level = level;
    this.group = group;
    this.description = description;
  }
  
  public static Issue fromKey(int key) {
    return KEYS.get(key);
  }
}
