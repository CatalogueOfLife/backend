package org.col.db.type;

import org.col.api.vocab.Lifezone;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class LifezoneSetTypeHandler extends BaseEnumSetTypeHandler<Lifezone> {
  
  public LifezoneSetTypeHandler() {
    super(Lifezone.class);
  }
}
