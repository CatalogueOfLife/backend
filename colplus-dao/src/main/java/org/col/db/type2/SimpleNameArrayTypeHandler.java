package org.col.db.type2;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.col.api.model.SimpleName;
import org.gbif.nameparser.api.Rank;

public class SimpleNameArrayTypeHandler extends BaseTypeHandler<List<SimpleName>> {
  
  private static CsvParser PARSER;
  static {
    CsvParserSettings cfg = new CsvParserSettings();
    cfg.setDelimiterDetectionEnabled(false);
    cfg.setQuoteDetectionEnabled(false);
    cfg.setReadInputOnSeparateThread(false);
    cfg.setSkipEmptyLines(true);
    cfg.setNullValue(null);
    cfg.setMaxColumns(3);
    cfg.setMaxCharsPerColumn(1024);
    PARSER = new CsvParser(cfg);
  }
  
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
  
  @VisibleForTesting
  protected static List<SimpleName> toList(Array pgArray) throws SQLException {
    List<SimpleName> cl = new ArrayList<>();
    if (pgArray != null) {
      Object[] obj = (Object[]) pgArray.getArray();
      for (Object o : obj) {
        // (k6,KINGDOM,Plantae)
        String row = o.toString();
        String[] cols = PARSER.parseLine(row.substring(1, row.length()-1));
        if (cols.length == 3) {
          Rank rank = cols[1] == null ? null : Rank.valueOf(cols[1]);
          cl.add(new SimpleName(cols[0], cols[2], rank));
        } else {
          // how can that be ?
          throw new TypeException("Failed to parse "+o+" to SimpleName");
        }
      }
    }
    return cl;
  }
}
