package life.catalogue.db.type;

import life.catalogue.db.type2.AbstractArrayTypeHandler;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * Base class for type handlers that need to convert between columns of enum array type and fields of type Set&lt;Enum&gt;.
 * Avoids nulls and uses empty enum sets instead.
 *
 * @param <T> enum class
 */
public abstract class BaseEnumSetTypeHandler<T extends Enum<T>> extends AbstractArrayTypeHandler<Set<T>> {

  private final Class<T> type;

  /**
   * @param nullToEmpty if true convert null values in the db to empty sets
   */
  protected BaseEnumSetTypeHandler(Class<T> enumClass, boolean nullToEmpty) {
    super(pgEnumName(enumClass), nullToEmpty ? EnumSet.noneOf(enumClass) : null);
    this.type = enumClass;
  }
  
  protected String toKey(T value) {
    return value == null ? null : value.name();
  }
  
  protected T fromKey(Object value) {
    return value == null ? null : Enum.valueOf(type, value.toString());
  }

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
    // getArray() reparses the pg array on every call, so read it once
    Object array = pgArray == null ? null : pgArray.getArray();
    if (array == null) {
      return nullValue != null ? EnumSet.noneOf(type) : null;
    }
    // postgres does not enforce dimensionality on array columns, so a botched migration can leave
    // multidimensional values behind. Without this check each element is a nested array whose
    // toString() ends up in Enum.valueOf, yielding a baffling "No enum constant Foo.[Ljava.lang.String;@1b6d"
    if (array.getClass().getComponentType().isArray()) {
      throw new SQLException(String.format(
        "multidimensional %s array found where a flat one is required. The %s column needs to be flattened, "
        + "e.g. UPDATE tbl SET col = ARRAY(SELECT unnest(col)) WHERE array_ndims(col) > 1", arrayType, arrayType));
    }
    return Arrays.stream((Object[]) array)
        .map(this::fromKey)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(type)));
  }
  
  public static String pgEnumName(Class clazz) {
    if (clazz.isMemberClass()) {
      return StringUtils.substringAfterLast(clazz.getName(),".").toUpperCase().replace('$', '_');
    } else {
      return clazz.getSimpleName().toUpperCase();
    }
  }
}
