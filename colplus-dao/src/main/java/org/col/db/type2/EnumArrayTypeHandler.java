package org.col.db.type2;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.col.api.util.VocabularyUtils;

/**
 * Converts a string array to a list of enums, allowing for duplicate enum constants and preserving the order found in the string array.
 */
public class EnumArrayTypeHandler<T extends Enum<T>> extends BaseTypeHandler<List<Enum<T>>> {

  private final Class<T> enumClass;

  public EnumArrayTypeHandler(Class<T> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<Enum<T>> parameter, JdbcType jdbcType) throws SQLException {
    throw new RuntimeException("not supported");
  }

  @Override
  public List<Enum<T>> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<Enum<T>> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<Enum<T>> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<Enum<T>> toList(Array pgArray) throws SQLException {
    if (pgArray == null) {
      return new ArrayList<>();
    }
    String[] strings = (String[]) pgArray.getArray();
    List<Enum<T>> enums = new ArrayList<>(strings.length);
    for (int i = 0; i < strings.length; i++) {
      Enum<T> e = VocabularyUtils.lookupEnum(strings[i], enumClass);
      enums.add(e);
    }
    return enums;
  }

}
