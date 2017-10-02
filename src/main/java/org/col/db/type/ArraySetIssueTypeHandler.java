package org.col.db.type;

import org.col.api.vocab.Lifezone;

/**
 * This type handler is based on a text[] postgres type.
 */
public class ArraySetIssueTypeHandler extends ArraySetTypeHandler<Lifezone> {

  public ArraySetIssueTypeHandler() {
    super("text");
  }

  @Override
  protected Lifezone convert(String x) {
    return Lifezone.valueOf(x);
  }
}
