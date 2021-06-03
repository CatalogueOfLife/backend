package life.catalogue.db.type;

import life.catalogue.api.model.DOI;

import java.sql.*;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * Handler for DOI types.
 *
 * @see DOI
 */
@MappedTypes(DOI.class)
public class DoiTypeHandler implements TypeHandler<DOI> {
  private static final Logger LOG = LoggerFactory.getLogger(DoiTypeHandler.class);
  
  @Override
  public void setParameter(PreparedStatement ps, int i, DOI parameter, JdbcType jdbcType) throws SQLException {
    if (parameter == null) {
      ps.setObject(i, null, Types.OTHER);
    } else {
      ps.setObject(i, parameter.getDoiName(), Types.OTHER);
    }
  }
  
  @Override
  public DOI getResult(ResultSet rs, String columnName) throws SQLException {
    return toDOI(rs.getString(columnName));
  }
  
  @Override
  public DOI getResult(ResultSet rs, int columnIndex) throws SQLException {
    return toDOI(rs.getString(columnIndex));
  }
  
  @Override
  public DOI getResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toDOI(cs.getString(columnIndex));
  }
  
  private static DOI toDOI(String val) throws SQLException {
    if (Strings.isNullOrEmpty(val)) {
      return null;
    }
    try {
      return new DOI(val);
    } catch (IllegalArgumentException e) {
      LOG.warn("Bad DOI found: {}", val);
    }
    return null;
  }
  
}
