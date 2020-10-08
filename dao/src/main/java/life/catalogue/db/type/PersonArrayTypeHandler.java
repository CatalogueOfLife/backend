package life.catalogue.db.type;

import life.catalogue.api.model.Person;
import life.catalogue.db.type2.CustomArrayAbstractTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.apache.ibatis.type.TypeException;

import java.sql.SQLException;
import java.util.List;

@MappedTypes(Person.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PersonArrayTypeHandler extends CustomArrayAbstractTypeHandler<Person> {

  public PersonArrayTypeHandler() {
    super("person");
  }

  @Override
  public String[] toAttributes(Person obj) throws SQLException {
    return new String[]{obj.getGivenName(), obj.getFamilyName(), obj.getEmail(), obj.getOrcid()};
  }

  @Override
  public Person fromAttributes(List<String> cols) throws SQLException {
    if (cols.size() == 4) {
      return new Person(cols.get(0), cols.get(1), cols.get(2), cols.get(3));
    } else {
      // how can that be ?
      throw new TypeException("Failed to parse "+String.join(",", cols)+" to Person");
    }
  }
}
