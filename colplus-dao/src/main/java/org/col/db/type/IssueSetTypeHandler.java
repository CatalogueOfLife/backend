package org.col.db.type;

import org.col.api.vocab.Issue;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class IssueSetTypeHandler extends BaseEnumSetTypeHandler<Issue> {
  
  public IssueSetTypeHandler() {
    super(Issue.class);
  }

}
