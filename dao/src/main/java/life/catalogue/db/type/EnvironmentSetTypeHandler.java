package life.catalogue.db.type;

import life.catalogue.api.vocab.Environment;

/**
 * A TypeHandler that converts between enum Environment constants and their ordinal
 * values.
 */
public class EnvironmentSetTypeHandler extends BaseEnumSetTypeHandler<Environment> {
  
  public EnvironmentSetTypeHandler() {
    super(Environment.class, true);
  }
}
