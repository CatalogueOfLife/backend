package life.catalogue.db.type;

import life.catalogue.api.vocab.InfoGroup;

/**
 * A TypeHandler that converts between enum Issue constants and their ordinal
 * values.
 */
public class InfoGroupSetTypeHandler extends BaseEnumSetTypeHandler<InfoGroup> {

  public InfoGroupSetTypeHandler() {
    super(InfoGroup.class, true);
  }

}
