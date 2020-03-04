package life.catalogue.db.type;

import life.catalogue.api.vocab.Lifezone;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class LifezoneSetTypeHandler extends BaseEnumSetTypeHandler<Lifezone> {
  
  public LifezoneSetTypeHandler() {
    super(Lifezone.class, true);
  }
}
