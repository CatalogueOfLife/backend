package org.col.db.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.common.base.Strings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;

/**
 * A simple converter for terms to simple prefixed name strings
 * analogous to the TermSerde for jackson.
 */
@MappedTypes(Term.class)
public class TermTypeHandler extends BaseTypeHandler<Term> {
  
  private static final TermFactory TERMFACTORY = TermFactory.instance();
  
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Term parameter, JdbcType jdbcType) throws SQLException {
    if (parameter instanceof UnknownTerm) {
      ps.setString(i, parameter.qualifiedName());
    } else {
      ps.setString(i, parameter.prefixedName());
    }
  }
  
  @Override
  public Term getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toTerm(rs.getString(columnName));
  }
  
  @Override
  public Term getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toTerm(rs.getString(columnIndex));
  }
  
  @Override
  public Term getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toTerm(cs.getString(columnIndex));
  }
  
  private static Term toTerm(String val) throws SQLException {
    if (Strings.isNullOrEmpty(val)) {
      return null;
    }
    return TERMFACTORY.findTerm(val);
  }
  
}
