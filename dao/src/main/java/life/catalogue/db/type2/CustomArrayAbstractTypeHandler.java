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
import java.util.ArrayList;
import java.util.List;

/**
 * Stores objects as arrays in postgres.
 * An optional nullValue parameter can be given to avoid nulls in the db and e.g. use empty arrray instead.
 */
public abstract class CustomArrayAbstractTypeHandler<T> extends BaseTypeHandler<List<T>> {
  protected final String typeName;

  /**
   * @param typeName type name of the arrays custom name
   */
  public CustomArrayAbstractTypeHandler(String typeName) {
    this.typeName = typeName;
  }
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<T> parameter, JdbcType jdbcType) throws SQLException {
    Object[] pgParams = new Object[parameter.size()];
    int x = 0;
    for (T p : parameter) {
      String[] cols = toAttributes(p);
      pgParams[x] = CustomAbstractTypeHandler.buildPgObject(typeName, cols);
      x++;
    }
    Array array = ps.getConnection().createArrayOf(typeName, pgParams);
    ps.setArray(i, array);
  }
  
  @Override
  public List<T> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toObj(rs.getArray(columnName));
  }
  
  @Override
  public List<T> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toObj(rs.getArray(columnIndex));
  }
  
  @Override
  public List<T> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toObj(cs.getArray(columnIndex));
  }

  public abstract String[] toAttributes(T obj) throws SQLException;

  public abstract T fromAttributes(List<String> cols) throws SQLException;

  public List<T> toObj(Array pgArray) throws SQLException {
    List<T> result = new ArrayList<>();
    if (pgArray != null) {
      Object[] objs = (Object[]) pgArray.getArray();
      for (Object o : objs) {
        result.add(fromAttributes(CustomAbstractTypeHandler.toCols(o)));
      }
    }
    return result;
  }
}
