/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.col.db.type;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.sql.*;
import java.util.List;

public class UriArrayTypeHandler extends BaseTypeHandler<List<URI>> {
  private static final Logger LOG = LoggerFactory.getLogger(UriArrayTypeHandler.class);

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<URI> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray());
    ps.setArray(i, array);
  }

  @Override
  public List<URI> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<URI> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<URI> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<URI> toList(Array pgArray) throws SQLException {
    if (pgArray == null) return Lists.newArrayList();

    List<URI> uris = Lists.newArrayList();
    for (String u : (String[]) pgArray.getArray()) {
      if (!Strings.isNullOrEmpty(u)) {
        try {
          uris.add(URI.create(u));
        } catch (Exception e) {
          LOG.error("Failed to convert pg array {} to URI for value {}",pgArray, u);
        }
      }
    }

    return uris;
  }
}
