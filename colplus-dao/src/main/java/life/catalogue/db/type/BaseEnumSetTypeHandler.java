package life.catalogue.db.type;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Base class for type handlers that need to convert between columns of type
 * integer[] and fields of type Set&lt;Enum&gt;.
 * Avoids nulls and uses empty arrays instead.
 *
 * @param <T> enum class
 */
public abstract class BaseEnumSetTypeHandler<T extends Enum<T>> extends BaseTypeHandler<Set<T>> {
  
  private final Class<T> type;
  private final String pgType;
  
  protected BaseEnumSetTypeHandler(Class<T> enumClass) {
    this.type = enumClass;
    pgType = pgEnumName(enumClass);
  }
  
  protected String toKey(T value) {
    return value == null ? null : value.name();
  }
  
  protected T fromKey(String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    String[] vals = new String[parameter.size()];
    int j = 0;
    for (T t : parameter) {
      vals[j++] = toKey(t);
    }
    Array array = ps.getConnection().createArrayOf(pgType, vals);
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    setNonNullParameter(ps, i, parameter == null ? Collections.emptySet() : parameter, jdbcType);
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
    if (pgArray != null && pgArray.getArray() != null && ((String[]) pgArray.getArray()).length > 0) {
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
