package org.col.db.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * Abstract base handler for all enums that expects a bidirectioanl conversion
 * to be implemented by subclasses.
 *
 * @param <T> the enumeration to be handled
 * @param <D> the db storage value type
 */
public abstract class BaseEnumTypeHandler<D, T extends Enum<?>> implements TypeHandler<T> {

  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    ps.setObject(i, fromEnum(parameter));
  }

  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    return toEnum((D) rs.getObject(columnName));
  }

  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    return toEnum((D) rs.getObject(columnIndex));
  }

  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toEnum((D) cs.getObject(columnIndex));
  }

  protected abstract T toEnum(D dbValue);

  protected abstract D fromEnum(T enumValue);
}
