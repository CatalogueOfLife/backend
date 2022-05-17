package life.catalogue.db.type;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import life.catalogue.api.model.Coordinate;
import life.catalogue.api.model.DOI;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeHandler;
import org.postgresql.geometric.PGpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.regex.Pattern;

/**
 * Handler for Coordinate java instances mapped to the native Postgres point type.
 *
 * @see Coordinate
 */
@MappedTypes(Coordinate.class)
public class PointTypeHandler implements TypeHandler<Coordinate> {
  private static final Logger LOG = LoggerFactory.getLogger(PointTypeHandler.class);
  // (1.234,2.345)
  private static final String DECIMAL = "-?\\d+(?:\\.\\d+)?";
  private static final Pattern POINT_PATTERN = Pattern.compile("\\(("+DECIMAL+"),("+DECIMAL+")\\)");

  @Override
  public void setParameter(PreparedStatement ps, int i, Coordinate parameter, JdbcType jdbcType) throws SQLException {
    if (parameter == null) {
      ps.setObject(i, null, Types.OTHER);
    } else {
      PGpoint p = new PGpoint(parameter.getLon(), parameter.getLat());
      //p = new PGpoint(2, parameter.getLat());
      ps.setObject(i, p, Types.OTHER);
    }
  }
  
  @Override
  public Coordinate getResult(ResultSet rs, String columnName) throws SQLException {
    return toCoord(rs.getString(columnName));
  }
  
  @Override
  public Coordinate getResult(ResultSet rs, int columnIndex) throws SQLException {
    return toCoord(rs.getString(columnIndex));
  }
  
  @Override
  public Coordinate getResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toCoord(cs.getString(columnIndex));
  }

  @VisibleForTesting
  static Coordinate toCoord(String val) throws SQLException {
    if (Strings.isNullOrEmpty(val)) {
      return null;
    }
    var m = POINT_PATTERN.matcher(val);
    if (m.find()) {
      return new Coordinate(Double.parseDouble(m.group(1)),Double.parseDouble(m.group(2)));
    }
    LOG.warn("Bad coordinate found: {}", val);
    return null;
  }
  
}
