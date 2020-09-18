package life.catalogue.db.type;

import com.fasterxml.jackson.core.type.TypeReference;
import life.catalogue.api.model.Person;
import life.catalogue.db.type2.JsonAbstractHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Person.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PersonJsonHandler extends JsonAbstractHandler<Person> {

  public PersonJsonHandler() {
    super(Person.class.getSimpleName(), new TypeReference<Person>() {});
  }
  
}
