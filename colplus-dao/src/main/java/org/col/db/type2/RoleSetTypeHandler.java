package org.col.db.type2;

import org.col.api.model.ColUser;
import org.col.db.type.EnumOrdinalSetTypeHandler;

/**
 * A TypeHandler that converts between enum ColUser.Role constants and their ordinal
 * values.
 */
public class RoleSetTypeHandler extends EnumOrdinalSetTypeHandler<ColUser.Role> {

  public RoleSetTypeHandler() {
    super(ColUser.Role.class);
  }
}
