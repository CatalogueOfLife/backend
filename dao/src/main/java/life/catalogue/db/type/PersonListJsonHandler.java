package life.catalogue.db.type;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.model.Person;
import life.catalogue.db.type2.JsonAbstractHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.util.List;

@MappedTypes(Person.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PersonListJsonHandler extends JsonAbstractHandler<List<Person>> {

  public PersonListJsonHandler() {
    super("PersonList", new TypeReference<List<Person>>() {});
  }
  
}
