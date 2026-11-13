package life.catalogue.api.vocab;

import life.catalogue.api.model.NameUsageBase;

import org.gbif.nameparser.api.NameType;

import java.util.Map;
import java.util.function.Function;

import static org.gbif.nameparser.api.NameType.*;

public enum IgnoreReason implements Described {

  NOMENCLATURAL_STATUS("The name has a nomenclatural status listed in the nameStatusExclusion of the sector or its profiles.",
    u -> u.getName().getNomStatus()),

  INDETERMINED("The name is indetermined, e.g. a species without an epithet, " +
    "or its authorship is only an indetermination marker like sp."),

  RANK("The accepted name has a rank outside the rank filter of the sector or its profiles. " +
    "Merges also skip unranked names that are not OTU identifiers like BOLD BINs or UNITE SH codes.",
    u -> u.getName().getRank()),

  EXTINCT("The extinct flag of the taxon does not match the extinct filter of the sector or its profiles.",
    u -> u.isTaxon() ? u.asTaxon().isExtinct() : null),

  INCONSISTENT_NAME("The name is a cultivar name, or in merges a name flagged as inconsistent."),

  NAME_FILTER("The scientific name does not fully match the name filter regex of the sector or its profiles.",
    u -> u.getName().getScientificName()),

  IGNORED_PARENT("A synonym of an ignored taxon, or in merges a usage whose ancestor was skipped.",
    NameUsageBase::getParentId),

  // https://github.com/CatalogueOfLife/data/issues/1718
  AMBIGUOUS_HOMONYM("A usage sharing the canonical name of an existing one, but with different authorship over a " +
    "classification too shallow to tell a real homonym from the same taxon. Skipped together with its " +
    "whole subtree rather than inserted as a duplicate.",
    u -> u.getName().getAuthorship()),

  // https://github.com/CatalogueOfLife/backend/issues/1608
  PLACEMENT_CONFLICT("A genus flagged with a placement conflict in its source that the project holds no single genus of the " +
    "same name for, so neither the source placement nor any project genus can be trusted. Skipped with its subtree."),

  BLOCKED_NAME("The name is on the blockedNames list or matches one of the blockedNamePatterns of the sector or its profiles.",
    u -> u.getName().getLabel()),

  ISSUE_EXCLUSION("The source name record carries an issue listed in the issueExclusion of the sector or its profiles."),

  TAXONOMIC_STATUS("The usage has a taxonomic status listed in the taxonomicStatusExclusion of the sector or its profiles.",
    NameUsageBase::getStatus),

  // ignored name types - one reason per NameType, named to match the NameType value
  NAME_SCIENTIFIC("A scientific name excluded by the name type filter of the sector or its profiles."),

  NAME_FORMULA("A hybrid formula excluded by the name type filter of the sector or its profiles."),

  NAME_INFORMAL("An informal name excluded by the name type filter of the sector or its profiles."),

  NAME_PLACEHOLDER("A placeholder name excluded by the name type filter of the sector or its profiles."),

  NAME_IDENTIFIER("An identifier like a BOLD BIN or UNITE SH code excluded by the name type filter of the sector or its profiles."),

  NAME_OTHER("A name of type other excluded by the name type filter of the sector or its profiles. " +
    "Merges also skip such unparsable names unless they follow the virus code, " +
    "the sector itself admits them or a reviewed decision includes them."),

  // historical: name-parser v4.2 dropped NameType.VIRUS (viruses are now OTHER carrying
  // NomCode.VIRUS). Kept so old import metrics still resolve, but no NameType maps to it.
  NAME_VIRUS("A virus name excluded by the name type filter of the sector. No longer used, viruses are names of type other.");

  private static final Map<NameType, IgnoreReason> nameTypes = Map.of(
    SCIENTIFIC, NAME_SCIENTIFIC,
    FORMULA, NAME_FORMULA,
    INFORMAL, NAME_INFORMAL,
    PLACEHOLDER, NAME_PLACEHOLDER,
    IDENTIFIER, NAME_IDENTIFIER,
    OTHER, NAME_OTHER
  );

  private final String description;
  private final Function<NameUsageBase, Object> valueExtractor;

  IgnoreReason(String description) {
    this(description, null);
  }

  IgnoreReason(String description, Function<NameUsageBase, Object> valueExtractor) {
    this.description = description;
    this.valueExtractor = valueExtractor;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public Function<NameUsageBase, Object> getValueExtractor() {
    return valueExtractor;
  }

  public static IgnoreReason reasonByNameType(NameType nameType) {
    return nameTypes.get(nameType);
  }
}
