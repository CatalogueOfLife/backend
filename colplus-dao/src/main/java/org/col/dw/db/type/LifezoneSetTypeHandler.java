package org.col.dw.db.type;

import org.col.dw.api.vocab.Lifezone;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class LifezoneSetTypeHandler extends EnumOrdinalSetTypeHandler<Lifezone> {

  public LifezoneSetTypeHandler() {
    super(Lifezone.class);
  }
}
