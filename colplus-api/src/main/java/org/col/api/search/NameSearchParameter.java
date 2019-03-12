package org.col.api.search;

import java.util.UUID;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public enum NameSearchParameter {

  DATASET_KEY(Integer.class),

  DECISION_KEY(Integer.class),

  FIELD(NameField.class),

  ISSUE(Issue.class),

  NAME_ID(String.class),

  /**
   * Searches on the name index id property of the Name which allows to share for same names across and within datasets.
   */
  NAME_INDEX_ID(String.class),

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
  TYPE(NameType.class);

  private final Class<?> type;

  NameSearchParameter(Class<?> type) {
    this.type = type;
  }

  public Class<?> type() {
    return type;
  }

}
