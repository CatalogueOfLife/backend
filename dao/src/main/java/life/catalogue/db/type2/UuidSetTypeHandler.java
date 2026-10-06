package life.catalogue.db.type2;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handler for Set<UUID> types.
 */
public class UuidSetTypeHandler extends AbstractArrayTypeHandler<Set<UUID>> {

  public UuidSetTypeHandler() {
    super("uuid", Collections.emptySet());
  }

  @Override
  public Object[] toArray(Set<UUID> obj) throws SQLException {
    return obj.toArray();
  }

  @Override
  public Set<UUID> toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return new HashSet<>();

    UUID[] values = (UUID[]) pgArray.getArray();
    // a stored array can hold nulls and duplicates, neither of which Set.of accepts
    Set<UUID> set = new HashSet<>();
    for (UUID v : values) {
      if (v != null) set.add(v);
    }
    return set;
  }

}
