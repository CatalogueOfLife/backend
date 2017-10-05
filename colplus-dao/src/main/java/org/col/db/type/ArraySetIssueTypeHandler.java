package org.col.db.type;

import org.col.api.vocab.Issue;

/**
 * This type handler is based on a text[] postgres type.
 */
public class ArraySetIssueTypeHandler extends ArraySetTypeHandler<Issue> {

  public ArraySetIssueTypeHandler() {
    super("text");
  }

  @Override
  protected Issue convert(String x) {
    return Issue.valueOf(x);
  }
}
