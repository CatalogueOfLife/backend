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
import java.util.Collection;
import java.util.Collections;

import com.google.common.collect.Lists;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * String array handler that avoids nulls and uses empty arrays instead.
 */
public class StringCollectionArrayTypeHandler extends BaseTypeHandler<Collection<String>> {
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Collection<String> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray());
    ps.setArray(i, array);
  }
  
  @Override
  public void setParameter(PreparedStatement ps, int i, Collection<String> parameter, JdbcType jdbcType) throws SQLException {
    setNonNullParameter(ps, i, parameter == null ? Collections.emptyList() : parameter, jdbcType);
  }
  
  @Override
  public Collection<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toCollection(rs.getArray(columnName));
  }
  
  @Override
  public Collection<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toCollection(rs.getArray(columnIndex));
  }
  
  @Override
  public Collection<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toCollection(cs.getArray(columnIndex));
  }
  
  private Collection<String> toCollection(Array pgArray) throws SQLException {
    if (pgArray == null) return Lists.newArrayList();
    
    String[] strings = (String[]) pgArray.getArray();
    return Lists.newArrayList(strings);
  }
}
