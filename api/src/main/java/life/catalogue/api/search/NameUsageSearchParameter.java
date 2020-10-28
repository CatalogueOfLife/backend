package life.catalogue.api.search;

import life.catalogue.api.model.EditorialDecision;
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

  NAME_ID(String.class),

  /**
   * Searches on the name index id property of the Name which allows to share for same names across and within datasets.
   * If a canonical, i.e. authorless, names index id is given it will also match all derived name index ids that represents names with an authorship
   * that have the same canonical id.
   */
  NAME_INDEX_ID(Integer.class),

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
  ALPHAINDEX(String.class);

  private final Class<?> type;

  private NameUsageSearchParameter(Class<?> type) {
    this.type = type;
  }

  public Class<?> type() {
    return type;
  }

}
