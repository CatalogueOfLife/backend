package org.col.dw.db.type2;

import org.col.dw.api.vocab.Issue;
import org.col.dw.db.type.EnumOrdinalSetTypeHandler;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class IssueSetTypeHandler extends EnumOrdinalSetTypeHandler<Issue> {

  public IssueSetTypeHandler() {
    super(Issue.class);
  }
}
