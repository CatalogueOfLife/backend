package org.col.db.type2;


import org.apache.ibatis.type.MappedTypes;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.type.BaseEnumTypeHandler;

/**
 * Special type handler for the taxonomic status enum which only allows ACCEPTED or PROVISIONALLY_ACCEPTED
 * based on the provisional boolean in the taxon table.
 */
@MappedTypes(TaxonomicStatus.class)
public class TaxonStatusTypeHandler extends BaseEnumTypeHandler<Boolean, TaxonomicStatus> {
  
  @Override
  protected Boolean fromEnum(TaxonomicStatus value) {
    return value != TaxonomicStatus.ACCEPTED;
  }
  
  @Override
  protected TaxonomicStatus toEnum(Boolean provisionallyAccepted) {
    return provisionallyAccepted ? TaxonomicStatus.PROVISIONALLY_ACCEPTED : TaxonomicStatus.ACCEPTED;
  }
}
