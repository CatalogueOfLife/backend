package life.catalogue.db.type2;

import life.catalogue.db.type.BaseEnumSetTypeHandler;

import org.gbif.nameparser.api.NameType;

public class NameTypeSetTypeHandler extends BaseEnumSetTypeHandler<NameType> {

  public NameTypeSetTypeHandler() {
    super(NameType.class, false);
  }

}
