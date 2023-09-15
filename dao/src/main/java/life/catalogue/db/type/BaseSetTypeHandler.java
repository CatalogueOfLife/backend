package life.catalogue.db.type;

import life.catalogue.db.type2.AbstractArrayTypeHandler;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for type handlers that need to convert between columns of array type and fields of type Set&lt;T&gt;.
 * Uses null for empty sets instead.
 *
 * @param <T> class
 */
public abstract class BaseSetTypeHandler<T> extends AbstractArrayTypeHandler<Set<T>> {

  protected BaseSetTypeHandler(String arrayType) {
    super(arrayType, null);
  }
  
  public String toKey(T value) {
    return value.toString();
  }

  abstract T fromKey(Object value);

  @Override
  public Object[] toArray(Set<T> obj) throws SQLException {
    String[] vals = new String[obj.size()];
    int j = 0;
    for (T t : obj) {
      vals[j++] = toKey(t);
    }
    return vals;
  }

  @Override
  public Set<T> toObj(Array pgArray) throws SQLException {
    if (pgArray == null || pgArray.getArray() == null) {
      return null;
    }

    if (((Object[]) pgArray.getArray()).length > 0) {
      return Arrays.stream(((Object[]) pgArray.getArray()))
          .map(this::fromKey)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
    }
    return null;
  }
}
