package life.catalogue.db.type;

import java.net.URI;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

/**
 * A simple converter for varchars to URI.
 */
@MappedTypes(URI.class)
public class UriTypeHandler extends BaseTypeHandler<URI> {
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, URI parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, parameter.toString());
  }
  
  @Override
  public URI getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toURI(rs.getString(columnName));
  }
  
  @Override
  public URI getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toURI(rs.getString(columnIndex));
  }
  
  @Override
  public URI getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toURI(cs.getString(columnIndex));
  }

  @VisibleForTesting
  protected static URI toURI(String val) throws SQLException {
    if (Strings.isNullOrEmpty(val)) {
      return null;
    }
    
    //return UrlParser.parse(val);
    // throws IllegalArgumentException
    return URI.create(val);
    
  }
  
}
