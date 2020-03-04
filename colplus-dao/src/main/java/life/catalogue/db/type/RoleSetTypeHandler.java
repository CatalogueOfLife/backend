package life.catalogue.db.type;

import life.catalogue.api.model.ColUser;

/**
 * A TypeHandler that converts between enum ColUser.Role constants and their ordinal
 * values.
 */
public class RoleSetTypeHandler extends BaseEnumSetTypeHandler<ColUser.Role> {
  
  public RoleSetTypeHandler() {
    super(ColUser.Role.class, true);
  }
}
