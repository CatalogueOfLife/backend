package life.catalogue.db.type;

import life.catalogue.common.date.FuzzyDate;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import com.google.common.base.Strings;

/**
 * A simple converter for text to FuzzyDate.
 */
@MappedTypes(FuzzyDate.class)
public class FuzzyDateTypeHandler extends BaseTypeHandler<FuzzyDate> {
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, FuzzyDate date, JdbcType jdbcType) throws SQLException {
    ps.setString(i, date.toString());
  }
  
  @Override
  public FuzzyDate getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toDate(rs.getString(columnName));
  }
  
  @Override
  public FuzzyDate getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toDate(rs.getString(columnIndex));
  }
  
  @Override
  public FuzzyDate getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toDate(cs.getString(columnIndex));
  }
  
  private static FuzzyDate toDate(String val) throws SQLException {
    if (Strings.isNullOrEmpty(val)) {
      return null;
    }
    return FuzzyDate.of(val);
  }
  
}
