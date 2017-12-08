package org.col.db.type;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for type handlers that need to convert between columns of type
 * integer[] and fields of type Set&lt;Enum&gt;.
 *
 * @param <T>
 */
public abstract class EnumOrdinalSetTypeHandler<T extends Enum<T>> extends BaseTypeHandler<Set<T>> {

  private final T[] values;
  private final Class<T> enumClass;

  public EnumOrdinalSetTypeHandler(Class<T> enumClass) {
    this.enumClass = enumClass;
    values = enumClass.getEnumConstants();
  }

  @Override
	public void setNonNullParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
		Integer[] ordinals = new Integer[parameter.size()];
		int j = 0;
		for (T t : parameter) {
			ordinals[j++] = t.ordinal();
		}
		Array array = ps.getConnection().createArrayOf("int", ordinals);
		ps.setArray(i, array);
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
		if (pgArray == null || pgArray.getArray() == null || ((Integer[])pgArray.getArray()).length==0) {
			return EnumSet.noneOf(enumClass);
		}
    return EnumSet.copyOf(Arrays.stream(((Integer[]) pgArray.getArray()))
        .map(i -> values[i])
        .collect(Collectors.toSet()));
  }

}
