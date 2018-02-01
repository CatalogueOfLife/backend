package org.col.dw.db.type;

/**
 *
 */

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import javax.annotation.Nullable;
import java.sql.*;
import java.util.Optional;

/**
 * Maps Java 8 {@link java.util.Optional} types to JDBC.
 * <p>
 * This mapping uses {@link
 * java.sql.ResultSet#getObject(int) getObject} and {@link java.sql.PreparedStatement#setObject(int, Object, int)
 * setObject} to store the underlying value, which means the underlying JDBC driver needs to have a useful
 * implementation of those methods.
 */
public class Java8OptionalTypeHandler implements TypeHandler<Optional<Object>> {

  public void setParameter(
      PreparedStatement ps, int i, @Nullable Optional<Object> parameter, @Nullable JdbcType jdbcType) throws SQLException {
    int typeCode = jdbcType == null ? Types.OTHER : jdbcType.TYPE_CODE;

    if (parameter == null) {
      ps.setNull(i, typeCode);
      return;
    }

    Object rawParameter = unpackOptional(parameter);
    if (rawParameter == null) {
      ps.setNull(i, typeCode);
      return;
    }

    assert rawParameter != null;
    ps.setObject(i, rawParameter, typeCode);
  }

  @Override
  public Optional<Object> getResult(ResultSet rs, String columnName) throws SQLException {
    Object rawValue = rs.getObject(columnName);
    return makeOptional(rawValue);
  }

  @Override
  public Optional<Object> getResult(ResultSet rs, int columnIndex) throws SQLException {
    Object rawValue = rs.getObject(columnIndex);
    return makeOptional(rawValue);
  }

  @Override
  public Optional<Object> getResult(CallableStatement cs, int columnIndex) throws SQLException {
    Object rawValue = cs.getObject(columnIndex);
    return makeOptional(rawValue);
  }

  /**
   * Unpack an Optional-like value to its contained value, or <code>null</code>. Subclasses must supply this: when
   * this method returns <code>null</code>, this TypeHandler will store an SQL <code>NULL</code> to the database. When
   * this method returns a non-null value, this TypeHandler will store it to the database using the appropriate
   * setObject method.
   *
   * @param parameter a non-null Optional-like value.
   * @return the unwrapped value, or <code>null</code>.
   */
  @Nullable
  private Object unpackOptional(Optional<Object> parameter) {
    return parameter.orElse(null);
  }

  /**
   * Wrap a database value in an Optional-like value. Subclasses must supply this: when passed <code>null</code> (read
   * from an SQL <code>NULL</code> value), this must return a non-null Optional-like value representing no value, such
   * as {@code Optional.absent()}. When passed a non-null value, this must wrap the value in an Optional-like wrapper,
   * such as {@code Optional.of(value)}.
   *
   * @param rawValue the raw value read from the database.
   * @return an Optional-like value wrapping <var>rawValue</var>.
   */
  private Optional<Object> makeOptional(Object rawValue) {
    if (rawValue == null)
      return Optional.empty();
    return Optional.of(rawValue);
  }
}
