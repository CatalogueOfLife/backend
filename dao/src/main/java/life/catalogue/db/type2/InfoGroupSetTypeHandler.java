package life.catalogue.db.type2;

import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

/**
 * A TypeHandler that converts between enum Issue constants and their ordinal
 * values.
 */
public class InfoGroupSetTypeHandler extends BaseEnumSetTypeHandler<InfoGroup> {

  public InfoGroupSetTypeHandler() {
    super(InfoGroup.class, true);
  }

}
