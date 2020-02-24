package life.catalogue.db.type2;

import life.catalogue.api.vocab.EntityType;
import life.catalogue.db.type.BaseEnumSetTypeHandler;

public class EntitySetTypeHandler extends BaseEnumSetTypeHandler<EntityType> {

  public EntitySetTypeHandler() {
    super(EntityType.class);
  }

}
