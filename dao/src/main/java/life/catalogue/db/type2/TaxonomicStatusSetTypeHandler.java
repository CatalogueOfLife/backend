package life.catalogue.db.type2;

import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

public class TaxonomicStatusSetTypeHandler extends BaseEnumSetTypeHandler<TaxonomicStatus> {

  public TaxonomicStatusSetTypeHandler() {
    super(TaxonomicStatus.class, false);
  }

}
