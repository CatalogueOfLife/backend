package life.catalogue.api.search;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.*;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.UUID;

/**
 * NOTE: the search parameters are used as http parameter names.
 * The standard CoL JSON serialization converts underscores to whitespace, which is not wanted in http parameter names.
 * Therefore avoid underscores at any cost !!!
 */
public enum NameUsageSearchParameter {

  USAGE_ID(String.class),

  DATASET_KEY(Integer.class),

  /**
   * This takes the datasetKey of the managed catalogue to filter decisions by, not usages. It will prune the list of decisions to just the
   * ones matching the datasetKey. I.e. the list only contains a single decision at max if one catalogue is given so the UI can quickly
   * determine if a decision exists at all for a given usage and does not have to ignore decisions from other catalogues.
   */
  CATALOGUE_KEY(Integer.class),

  /**
   * Allows to filter usages based on the existence of a decision with the matching MODE.
   * _NOT_NULL or _NULL can be used here to filter usages to just the ones with or without any decision (from the requested DECISION_DATASET_KEY if given)
   */
  DECISION_MODE(EditorialDecision.Mode.class),

  FIELD(NameField.class),

  ISSUE(Issue.class),

  GROUP(TaxGroup.class),

  NAME_ID(String.class),

  /**
   * Nomenclatural code.
   */
  NOM_CODE(NomCode.class),

  /**
   * Nomenclatural status of the name alone
   */
  NOM_STATUS(NomStatus.class),

  /**
   * The GBIF publisher key from the dataset
   */
  PUBLISHER_KEY(UUID.class),

  /**
   * Rank
   */
  RANK(Rank.class),

  /**
   * ReferenceID of the Name.publishedInID
   */
  PUBLISHED_IN_ID(String.class),

  /**
   * The sector key attached to a taxon. Synonyms inherit the key by their accepted taxon, but do not expose the key on the Synonym instance
   * itself.
   */
  SECTOR_KEY(Integer.class),

  /**
   * The subject dataset key of the corresponding sector attached to a taxon. Synonyms inherit the key by their accepted taxon, but do not expose
   * the key on the Synonym instance itself.
   */
  SECTOR_DATASET_KEY(Integer.class),

  /**
   * The publisher key of the subject dataset of the corresponding sector attached to a name usage.
   */
  SECTOR_PUBLISHER_KEY(UUID.class),

  /**
   * The mode of the corresponding sector attached to a name usage.
   * Can be used to look for "extended" records from MERGE sectors only in XReleases.
   */
  SECTOR_MODE(Sector.Mode.class),

  /**
   * Dataset key of the secondary source linked to a name usage.
   * Only exists in XReleases.
   */
  SECONDARY_SOURCE(Integer.class),

  /**
   * InfoGroup of a secondary source that must exist for a name usage.
   * Not that if combined with secondary source parameter there is no guarantee the secondary source is for that exact info group.
   * Only exists in XReleases.
   */
  SECONDARY_SOURCE_GROUP(InfoGroup.class),

  /**
   * TaxonomicStatus
   */
  STATUS(TaxonomicStatus.class),

  /**
   * A taxonID that searches on the entire classification of a Taxon or its Synonyms. E.g. searching by the taxonID for Coleoptera should
   * return all name usages within that beetle order, including synonyms.
   */
  TAXON_ID(String.class),

  /**
   * Name.type
   */
  NAME_TYPE(NameType.class),

  EXTINCT(Boolean.class),

  ENVIRONMENT(Environment.class),
  
  AUTHORSHIP(String.class),
  
  AUTHORSHIP_YEAR(String.class),

  /**
   * First, upper cased character of the name.
   * Allows to built an alphabetical index to all search results,
   * see https://github.com/CatalogueOfLife/backend/issues/236
   */
  ALPHAINDEX(String.class),

  /**
   * The name usage origin
   */
  ORIGIN(Origin.class),

  /**
   * Unsafe mode that avoids request validations
   */
  UNSAFE(Boolean.class)
  ;

  private final Class<?> type;

  private NameUsageSearchParameter(Class<?> type) {
    this.type = type;
  }

  public Class<?> type() {
    return type;
  }

}
