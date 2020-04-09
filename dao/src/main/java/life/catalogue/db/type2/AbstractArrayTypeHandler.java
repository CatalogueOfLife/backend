/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyTaxon of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.db.type2;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.util.Set;

/**
 * Stores objects as arrays in postgres.
 * An optional nullValue parameter can be given to avoid nulls in the db and e.g. use empty arrray instead.
 */
public abstract class AbstractArrayTypeHandler<T> extends BaseTypeHandler<T> {
  protected final String arrayType;
  protected final T nullValue;

  /**
   * @param nullValue value to use for nulls in the database, ideally immutable
   */
  public AbstractArrayTypeHandler(String arrayType, T nullValue) {
    this.arrayType = arrayType;
    this.nullValue = nullValue;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf(arrayType, toArray(parameter));
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    if (parameter == null && nullValue == null) {
      ps.setArray(i, null);
    } else {
      setNonNullParameter(ps, i, parameter == null ? nullValue : parameter, jdbcType);
    }
  }
  
  @Override
  public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toObj(rs.getArray(columnName));
  }
  
  @Override
  public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toObj(rs.getArray(columnIndex));
  }
  
  @Override
  public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toObj(cs.getArray(columnIndex));
  }

  public abstract Object[] toArray(T obj) throws SQLException;

  public abstract T toObj(Array pgArray) throws SQLException;
}
