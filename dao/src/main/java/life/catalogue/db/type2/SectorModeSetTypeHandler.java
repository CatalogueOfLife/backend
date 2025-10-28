package life.catalogue.db.type2;

import life.catalogue.api.model.Sector;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

/**
 * A TypeHandler that converts between enum User.Role constants and their ordinal
 * values.
 */
public class SectorModeSetTypeHandler extends BaseEnumSetTypeHandler<Sector.Mode> {

  public SectorModeSetTypeHandler() {
    super(Sector.Mode.class, true);
  }
}
