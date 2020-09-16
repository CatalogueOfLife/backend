package life.catalogue.db.type;

import life.catalogue.api.vocab.Issue;

/**
 * A TypeHandler that converts between enum Issue constants and their ordinal
 * values.
 */
public class IssueSetTypeHandler extends BaseEnumSetTypeHandler<Issue> {
  
  public IssueSetTypeHandler() {
    super(Issue.class, true);
  }

}
