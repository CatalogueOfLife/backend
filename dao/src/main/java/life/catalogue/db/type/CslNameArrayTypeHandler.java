package life.catalogue.db.type;

import life.catalogue.api.model.CslName;
import life.catalogue.db.type2.CustomArrayAbstractTypeHandler;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;

/**
 * A postgres type handler to persist arrays of CslName instances.
 * Note that only the core given, family, nonDroppingParticle and literal properties are persisted!
 */
@MappedTypes(CslName.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class CslNameArrayTypeHandler extends CustomArrayAbstractTypeHandler<CslName> {
  static final String PGTYPE = "cslname";

  public CslNameArrayTypeHandler() {
    super(PGTYPE);
  }

  @Override
  public String[] toAttributes(CslName obj) throws SQLException {
    return new String[]{obj.getGiven(), obj.getFamily(), obj.getNonDroppingParticle()};
  }

  @Override
  public CslName fromAttributes(List<String> cols) throws SQLException {
    if (cols.size() == 3) {
      return new CslName(cols.get(0), cols.get(1), cols.get(2));
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse " + String.join(",", cols) + " to CslName");
    }
  }
}
