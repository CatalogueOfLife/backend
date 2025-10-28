package life.catalogue.db.type2;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

/**
 * A TypeHandler that converts between enum TaxGroup constants and their ordinal
 * values.
 */
public class TaxGroupSetTypeHandler extends BaseEnumSetTypeHandler<TaxGroup> {

  public TaxGroupSetTypeHandler() {
    super(TaxGroup.class, true);
  }

}
