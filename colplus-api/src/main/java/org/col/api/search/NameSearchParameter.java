package org.col.api.search;

import org.col.api.util.VocabularyUtils;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public enum NameSearchParameter {

  DATASET_KEY(Integer.class),

  /**
   * Rank
   */
  RANK(Rank.class),

  NOM_STATUS(NomStatus.class),

  /**
   * TaxonomicStatus
   */
  STATUS(TaxonomicStatus.class),

  ISSUE(Issue.class),

  /**
   * Name.type
   */
  TYPE(NameType.class),

  FIELD(NameField.class),

  NAME_ID(String.class),

  NAME_INDEX_ID(String.class),

  PUBLISHED_IN_ID(String.class);

  private final Class<?> type;

  NameSearchParameter(Class<?> type) {
    this.type = type;
  }

  public Class<?> type() {
    return type;
  }

  /**
   * Checks that the provided string can be converted to the type of this NameSearchParameter
   * 
   * @param value
   * @return
   */
  @SuppressWarnings("unchecked")
  public boolean isLegalValue(Object value) {
    if (!NameSearchRequest.isLiteral(value.toString()) || type == String.class) {
      return true;
    }
    if (type == Integer.class) {
      try {
        Integer.parseInt(value.toString());
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    if (type.isEnum()) {
      if (value.getClass() == type) {
        return true;
      }
      return VocabularyUtils.lookup(value.toString(), (Class<? extends Enum<?>>) type).isPresent();
    }
    throw new IllegalArgumentException(NameSearchParameter.class.getSimpleName() + " missing converter for data type " + type);
  }
}
