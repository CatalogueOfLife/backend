package life.catalogue.db.type;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for type handlers that need to convert between columns of enum array type and fields of type Set&lt;Enum&gt;.
 * Avoids nulls and uses empty arrays instead.
 *
 * @param <T> enum class
 */
public abstract class BaseEnumSetTypeHandler<T extends Enum<T>> extends BaseTypeHandler<Set<T>> {
  
  private final Class<T> type;
  private final String pgType;
  private final boolean nullToEmpty;

  /**
   * @param nullToEmpty if true convert null values in the db to empty sets
   */
  protected BaseEnumSetTypeHandler(Class<T> enumClass, boolean nullToEmpty) {
    this.type = enumClass;
    pgType = pgEnumName(enumClass);
    this.nullToEmpty = nullToEmpty;
  }
  
  protected String toKey(T value) {
    return value == null ? null : value.name();
  }
  
  protected T fromKey(String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    Array array;
    if (parameter == null) {
      array = null;
    } else {
      String[] vals = new String[parameter.size()];
      int j = 0;
      for (T t : parameter) {
        vals[j++] = toKey(t);
      }
      array = ps.getConnection().createArrayOf(pgType, vals);

    }
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    setNonNullParameter(ps, i, parameter == null && nullToEmpty ? Collections.emptySet() : parameter, jdbcType);
  }
  
  @Override
  public Set<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return convert(rs.getArray(columnName));
  }
  
  @Override
  public Set<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return convert(rs.getArray(columnIndex));
  }
  
  @Override
  public Set<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return convert(cs.getArray(columnIndex));
  }
  
  private Set<T> convert(Array pgArray) throws SQLException {
    if (pgArray == null || pgArray.getArray() == null) {
      return nullToEmpty ? EnumSet.noneOf(type) : null;
    }

    if (((String[]) pgArray.getArray()).length > 0) {
      Set<T> set = Arrays.stream(((String[]) pgArray.getArray()))
          .map(this::fromKey)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
      if (!set.isEmpty()) {
        return EnumSet.copyOf(set);
      }
    }
    return EnumSet.noneOf(type);
  }
  
  public static String pgEnumName(Class clazz) {
    if (clazz.isMemberClass()) {
      return StringUtils.substringAfterLast(clazz.getName(),".").toUpperCase().replace('$', '_');
    } else {
      return clazz.getSimpleName().toUpperCase();
    }
  }
}
