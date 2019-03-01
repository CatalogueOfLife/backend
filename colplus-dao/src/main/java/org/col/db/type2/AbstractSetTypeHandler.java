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
package org.col.db.type2;

import java.sql.*;
import java.util.Collections;
import java.util.Set;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * Stores sets as non null arrays in postgres, avoiding nulls and uses empty sets instead.
 */
public abstract class AbstractSetTypeHandler<T> extends BaseTypeHandler<Set<T>> {
  private final String arrayType;
  
  public AbstractSetTypeHandler(String arrayType) {
    this.arrayType = arrayType;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf(arrayType, parameter.toArray());
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, Set<T> parameter, JdbcType jdbcType) throws SQLException {
    setNonNullParameter(ps, i, parameter == null ? Collections.emptySet() : parameter, jdbcType);
  }
  
  @Override
  public Set<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toSet(rs.getArray(columnName));
  }
  
  @Override
  public Set<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toSet(rs.getArray(columnIndex));
  }
  
  @Override
  public Set<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toSet(cs.getArray(columnIndex));
  }
  
  abstract Set<T> toSet(Array pgArray) throws SQLException;
}
