package life.catalogue.db.type;

import life.catalogue.api.model.User;

/**
 * A TypeHandler that converts between enum User.Role constants and their ordinal
 * values.
 */
public class RoleSetTypeHandler extends BaseEnumSetTypeHandler<User.Role> {
  
  public RoleSetTypeHandler() {
    super(User.Role.class, true);
  }
}
