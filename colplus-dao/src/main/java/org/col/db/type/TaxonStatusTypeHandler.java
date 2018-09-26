package org.col.db.type;


import org.apache.ibatis.type.MappedTypes;
import org.col.api.vocab.TaxonomicStatus;

/**
 * Special type handler for the taxonomic status enum which only allows ACCEPTED or DOUBTFUL
 * based on the doubtful boolean in the taxon table.
 */
@MappedTypes(TaxonomicStatus.class)
public class TaxonStatusTypeHandler extends BaseEnumTypeHandler<Boolean, TaxonomicStatus> {

  @Override
  public Boolean fromEnum(TaxonomicStatus value) {
    return value != TaxonomicStatus.ACCEPTED;
  }

  @Override
  public TaxonomicStatus toEnum(Boolean doubtful) {
    return doubtful ? TaxonomicStatus.DOUBTFUL : TaxonomicStatus.ACCEPTED;
  }
}
