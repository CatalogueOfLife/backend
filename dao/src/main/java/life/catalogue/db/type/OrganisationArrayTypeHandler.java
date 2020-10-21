package life.catalogue.db.type;

import life.catalogue.api.model.Organisation;
import life.catalogue.api.vocab.Country;
import life.catalogue.db.type2.CustomArrayAbstractTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;

import java.sql.SQLException;
import java.util.List;

@MappedTypes(Organisation.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class OrganisationArrayTypeHandler extends CustomArrayAbstractTypeHandler<Organisation> {

  public OrganisationArrayTypeHandler() {
    super("organisation");
  }

  @Override
  public String[] toAttributes(Organisation obj) throws SQLException {
    return new String[]{obj.getName(), obj.getDepartment(), obj.getCity(), obj.getCountry() == null ? null : obj.getCountry().getIso2LetterCode()};
  }

  @Override
  public Organisation fromAttributes(List<String> cols) throws SQLException {
    if (cols.size() == 4) {
      return new Organisation(cols.get(0), cols.get(1), cols.get(2), Country.fromIsoCode(cols.get(3)).orElse(null));
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse "+String.join(",", cols)+" to Organisation");
    }
  }
}
