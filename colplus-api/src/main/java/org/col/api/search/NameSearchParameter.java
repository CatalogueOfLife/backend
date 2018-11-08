package org.col.api.search;

import org.apache.commons.lang3.StringUtils;
import org.col.api.util.VocabularyUtils;
import org.col.api.vocab.*;
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
   * Converts a string into a instance of the facets data type. Throws RuntimeException if not
   * possible.
   *
   * @return the converted string value
   */
  public Object from(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    if (String.class.isAssignableFrom(type)) {
      return value;
    } else if (Integer.class.isAssignableFrom(type)) {
      return Integer.valueOf(value);
    } else if (type.isEnum()) {
      return VocabularyUtils.lookupEnum(value, (Class<? extends Enum<?>>) type);
    } else {
      throw new IllegalArgumentException(NameSearchParameter.class.getSimpleName() + " missing converter for data type " + type);
    }
  }
}
