package org.col.db.type2;

import org.col.api.vocab.Issue;
import org.col.db.type.EnumOrdinalSetTypeHandler;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class IssueSetTypeHandler extends EnumOrdinalSetTypeHandler<Issue> {
  
  public IssueSetTypeHandler() {
    super(Issue.class);
  }
}
