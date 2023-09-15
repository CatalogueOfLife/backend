package life.catalogue.db.type;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for Set<UUID> types.
 */
public class UuidSetTypeHandler extends BaseSetTypeHandler<UUID> {

  public UuidSetTypeHandler() {
    super("uuid");
  }

  @Override
  UUID fromKey(Object val) {
    return (UUID) val;
  }

}
