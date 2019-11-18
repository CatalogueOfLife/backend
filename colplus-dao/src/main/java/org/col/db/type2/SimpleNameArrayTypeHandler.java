package org.col.db.type2;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.col.api.model.SimpleName;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleNameArrayTypeHandler extends BaseTypeHandler<List<SimpleName>> {
  
  private static final Logger LOG = LoggerFactory.getLogger(SimpleNameArrayTypeHandler.class);
  private static Pattern PARSER = Pattern.compile("^\\(([^,]+),([A-Z_]+),(.*)\\)$");
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, List<SimpleName> parameter, JdbcType jdbcType) throws SQLException {
    throw new RuntimeException("writes not supported");
  }
  
  @Override
  public List<SimpleName> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }
  
  @Override
  public List<SimpleName> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }
  
  @Override
  public List<SimpleName> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }
  
  private List<SimpleName> toList(Array pgArray) throws SQLException {
    List<SimpleName> cl = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        Matcher m = PARSER.matcher(o.toString());
        if (m.find()) {
          Rank rank = Rank.valueOf(m.group(2).toUpperCase());
          cl.add(new SimpleName(m.group(1), m.group(3), rank));
        } else {
          // can that really be???
          throw new TypeException("Failed to parse "+o+" to SimpleName");
        }
      }
    }
    return cl;
  }
}
