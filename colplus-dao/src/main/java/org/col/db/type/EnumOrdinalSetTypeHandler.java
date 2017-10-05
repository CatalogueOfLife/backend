package org.col.db.type;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Base class for type handlers that need to convert between columns of type
 * integer[] and fields of type Set&lt;Enum&gt;.
 *
 * @param <T>
 */
public abstract class EnumOrdinalSetTypeHandler<T extends Enum<T>> extends BaseTypeHandler<Set<T>> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType)
	    throws SQLException {
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
		if (pgArray == null) {
			return Collections.<T>emptySet();
		}
		Integer[] ordinals = (Integer[]) pgArray.getArray();
		List<T> constants = new ArrayList<>(ordinals.length);
		for (int ordinal : ordinals) {
			T converted = getEnumClass().getEnumConstants()[ordinal];
			constants.add(converted);
		}
		return new LinkedHashSet<>(constants);
	}

	protected abstract Class<T> getEnumClass();

}
