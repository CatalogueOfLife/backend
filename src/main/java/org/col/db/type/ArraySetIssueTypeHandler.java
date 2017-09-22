package org.col.db.type;

import org.col.api.vocab.NameIssue;

/**
 * This type handler is based on a text[] postgres type.
 */
public class ArraySetIssueTypeHandler extends ArraySetTypeHandler<NameIssue> {

  public ArraySetIssueTypeHandler() {
    super("text");
  }

  @Override
  protected NameIssue convert(String x) {
    return NameIssue.valueOf(x);
  }
}
