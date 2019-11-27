package life.catalogue.db.type;

import life.catalogue.db.type.BaseEnumSetTypeHandler;
import life.catalogue.api.vocab.Issue;

/**
 * A TypeHandler that converts between enum Lifezone constants and their ordinal
 * values.
 */
public class IssueSetTypeHandler extends BaseEnumSetTypeHandler<Issue> {
  
  public IssueSetTypeHandler() {
    super(Issue.class);
  }

}
