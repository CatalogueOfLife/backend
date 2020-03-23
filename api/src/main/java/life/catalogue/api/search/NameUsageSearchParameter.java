package life.catalogue.api.search;

import java.util.UUID;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NameField;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.api.vocab.TaxonomicStatus;

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
   * Allows to filter usages based on the existence of a decision with the matching MODE. NOT_NULL or NULL can be used here to filter usages
   * to just the ones with or without any decision (from the requested DECISION_DATASET_KEY if given)
   */
  DECISION_MODE(EditorialDecision.Mode.class),

  FIELD(NameField.class),

  ISSUE(Issue.class),

  NAME_ID(String.class),

  /**
   * Searches on the name index id property of the Name which allows to share for same names across and within datasets.
   */
  NAME_INDEX_ID(String.class),

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
   * The dataset key of the corresponding sector attached to a taxon. Synonyms inherit the key by their accepted taxon, but do not expose
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
  TYPE(NameType.class),

  FOSSIL(Boolean.class),

  RECENT(Boolean.class),

  ALPHAINDEX(Character.class);

  private final Class<?> type;

  private NameUsageSearchParameter(Class<?> type) {
    this.type = type;
  }

  public Class<?> type() {
    return type;
  }

}
